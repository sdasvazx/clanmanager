package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.LoginRequestDto;
import com.clanmanager.clanmanager.dto.RegisterRequestDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.security.PasswordSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void publicRegistrationWaitsForAdminApproval() {
        MemberRepository repository = mock(MemberRepository.class);
        when(repository.count()).thenReturn(10L);
        when(repository.save(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            saved.setMemberId(207L);
            return saved;
        });

        RegisterRequestDto request = new RegisterRequestDto();
        request.setCharacterName("신규회원");
        request.setPassword(PasswordSupport.DEFAULT_INITIAL_PASSWORD);
        request.setCombatPower(1_000_000);

        Map<String, Object> response = new AuthController(repository).register(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(repository).save(memberCaptor.capture());
        Member saved = memberCaptor.getValue();
        assertThat(response.get("approvalPending")).isEqualTo(true);
        assertThat(saved.getActive()).isFalse();
        assertThat(saved.getStatus()).isEqualTo("가입승인대기");
    }

    @Test
    void existingMemberUsingInitialPasswordIsForcedToChangeIt() {
        MemberRepository repository = mock(MemberRepository.class);
        Member member = Member.builder()
                .memberId(1L)
                .characterName("테스트")
                .password(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD))
                .mustChangePassword(false)
                .role(MemberRole.MEMBER)
                .active(true)
                .build();
        when(repository.findByCharacterName("테스트")).thenReturn(Optional.of(member));
        when(repository.save(member)).thenReturn(member);

        LoginRequestDto request = new LoginRequestDto();
        request.setCharacterName("테스트");
        request.setPassword(PasswordSupport.DEFAULT_INITIAL_PASSWORD);

        Map<String, Object> response = new AuthController(repository).login(request);

        assertThat(response.get("mustChangePassword")).isEqualTo(true);
        assertThat(member.getMustChangePassword()).isTrue();
        verify(repository).save(member);
    }
}
