package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.LoginRequestDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.security.PasswordSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

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
