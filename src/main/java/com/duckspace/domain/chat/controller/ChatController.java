package com.duckspace.domain.chat.controller;

import com.duckspace.domain.chat.dto.request.CreateRoomRequest;
import com.duckspace.domain.chat.dto.request.SendMessageRequest;
import com.duckspace.domain.chat.dto.response.ChatMessageResponse;
import com.duckspace.domain.chat.dto.response.ChatRoomResponse;
import com.duckspace.domain.chat.service.ChatService;
import com.duckspace.global.auth.AuthUser;
import com.duckspace.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "채팅", description = "덕톡라운지 1:1 채팅 (폴링 방식)")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "채팅방 생성 또는 조회",
            description = "상대방과의 채팅방을 반환합니다. 없으면 만들고, 여러 번 호출해도 방은 하나만 생깁니다.")
    @PostMapping("/rooms")
    public ApiResponse<ChatRoomResponse> createOrGetRoom(@AuthenticationPrincipal AuthUser authUser,
                                                          @Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.success(chatService.createOrGetRoom(authUser.getUserId(), request.partnerId()));
    }

    @Operation(summary = "내 채팅방 목록",
            description = "마지막 메시지가 최근인 순서로 반환합니다. 안 읽은 메시지가 있으면 hasUnread 가 true 입니다.")
    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomResponse>> getMyRooms(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(chatService.getMyRooms(authUser.getUserId()));
    }

    @Operation(summary = "메시지 조회 (폴링 · 지난 대화)",
            description = """
                    응답은 항상 오래된 순입니다. 커서에 따라 세 방향으로 동작합니다.
                    - 둘 다 비움: 최근 메시지 (최초 진입)
                    - after: 그 messageId 이후만 (폴링 — 마지막으로 받은 id 를 넣어 3초 간격 호출)
                    - before: 그 messageId 이전만 (위로 스크롤 — 화면에 있는 가장 오래된 id 를 넣으세요)
                    after 와 before 를 같이 주면 400 입니다.
                    읽음 처리는 최초 진입·폴링에서만 됩니다.
                    **지난 대화의 끝은 응답이 비었을 때로 판단하세요.** size 는 서버가 최대 100 으로
                    깎기 때문에, 그보다 큰 값을 보내고 "응답이 size 보다 짧으면 끝" 으로 보면
                    아직 남은 대화가 있는데도 끝난 것으로 오판하게 됩니다.
                    """)
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<List<ChatMessageResponse>> getMessages(@AuthenticationPrincipal AuthUser authUser,
                                                               @PathVariable Long roomId,
                                                               @RequestParam(required = false) Long after,
                                                               @RequestParam(required = false) Long before,
                                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.success(chatService.getMessages(authUser.getUserId(), roomId, after, before, size));
    }

    @Operation(summary = "메시지 전송")
    @PostMapping("/rooms/{roomId}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long roomId,
                                                         @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(chatService.sendMessage(authUser.getUserId(), roomId, request.content()));
    }
}
