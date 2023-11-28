package com.growstory.domain.qnachat.chatmessage.service;

import com.growstory.domain.account.entity.Account;
import com.growstory.domain.account.service.AccountService;
import com.growstory.domain.images.entity.ChatMessageImage;
import com.growstory.domain.images.service.ChatMessageImageService;
import com.growstory.domain.qnachat.chatmessage.constant.ChatMessageConstants;
import com.growstory.domain.qnachat.chatmessage.dto.ChatMessageRequestDto;
import com.growstory.domain.qnachat.chatmessage.dto.ChatMessageResponseDto;
import com.growstory.domain.qnachat.chatmessage.entity.ChatMessage;
import com.growstory.domain.qnachat.chatmessage.repository.ChatMessageRepository;
import com.growstory.domain.qnachat.chatroom.constants.ChatRoomConstants;
import com.growstory.domain.qnachat.chatroom.dto.EnumChatRoomRequestDto;
import com.growstory.domain.qnachat.chatroom.entity.AccountChatRoom;
import com.growstory.domain.qnachat.chatroom.entity.ChatRoom;
import com.growstory.domain.qnachat.chatroom.service.ChatRoomService;
import com.growstory.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Transactional
@Service
public class ChatMessageServiceImpl implements ChatMessageService{
    private final ChatRoomService chatRoomService;
    private final ChatMessageRepository chatMessageRepository;
    private final AccountService accountService;
    private final ChatMessageImageService chatMessageImageService;

    private static final String CHAT_MESSAGE_IMAGE_PROCESS_TYPE = "chat_message_image";

    // chatRoomId -> 메시지응답 페이지 반환
    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ChatMessageResponseDto>> getAllChatMessage(Pageable pageable, Long chatRoomId) {
        ChatRoom chatRoom = this.chatRoomService.findVerifiedChatRoom(chatRoomId);
        Page<ChatMessage> page = this.chatMessageRepository.findByChatRoom(pageable, chatRoom);
        List<ChatMessageResponseDto> data = page.get().map(ChatMessageResponseDto::from).collect(Collectors.toList());
        return PageResponse.of(page, data);
    }

    // 메시지 전송 요청 -> Account, ChatRoom과 매핑 후 저장 및 응답
    @Override
    public ChatMessageResponseDto createSendMessage(ChatMessageRequestDto chatMessageRequest, MultipartFile image) {
        Account account = accountService.findVerifiedAccount(chatMessageRequest.getSenderId());
        ChatRoom chatRoom = chatRoomService.findVerifiedChatRoom(chatMessageRequest.getChatRoomId());
        ChatMessage chatMessage =
                ChatMessage.builder()
                        .account(account)
                        .chatRoom(chatRoom)
                        .message(chatMessageRequest.getMessage())
                        .build();
        chatMessageRepository.save(chatMessage);

        // image가 null이 아닐 경우 이미지 업로드 및 DB 저장
        if(image!=null && !image.isEmpty()) {
            mapChatMessageWithImage(image, chatMessage);
        }

        return ChatMessageResponseDto.from(chatMessage);
    }

    // 채팅방 입장 메시지 매핑 및 저장, 응답
    @Override
    public ChatMessageResponseDto createEnterMessage(EnumChatRoomRequestDto chatMessageRequest) {
        Long accountId = chatMessageRequest.getSenderId();
        Long chatRoomId = chatMessageRequest.getChatRoomId();
        Account account = accountService.findVerifiedAccount(accountId);
        ChatRoom chatRoom = chatRoomService.findVerifiedChatRoom(chatRoomId);
        AccountChatRoom accountChatRoom = chatRoomService.validateIsEntered(accountId, chatRoomId);
        chatRoomService.validateAlreadyEnter(accountId, chatRoomId);
        accountChatRoom.updateEntryCheck(true);

        ChatMessage chatMessage =
                ChatMessage.builder()
                        .message("안녕하세요, "+ account.getDisplayName()+ ChatMessageConstants.EnumChatMessage.ENTERED.getValue())
                        .account(account)
                        .chatRoom(chatRoom)
                        .build();

        chatMessageRepository.save(chatMessage);

        return ChatMessageResponseDto.from(chatMessage);
    }

    private void mapChatMessageWithImage(MultipartFile image, ChatMessage chatMessage) {
        ChatMessageImage chatMessageImage
                = chatMessageImageService.createChatMessageImgWithS3(image, CHAT_MESSAGE_IMAGE_PROCESS_TYPE, chatMessage);
        chatMessage.updateChatMessageImage(chatMessageImage);
    }

    // 채팅방 삭제 메시지 전송 & 채팅방 떠나기
    @Override
    public ChatMessageResponseDto sendExitChatRoomMessage(EnumChatRoomRequestDto deleteChatRoomRequest) {
        Account account = accountService.findVerifiedAccount(deleteChatRoomRequest.getSenderId());
        ChatRoom chatRoom = chatRoomService.findVerifiedChatRoom(deleteChatRoomRequest.getChatRoomId());
        AccountChatRoom accountChatRoom = chatRoomService.getAccountChatRoomByAccountIdAndChatRoomId(account.getAccountId(), chatRoom.getChatRoomId());
        // 채팅방 떠나기
        chatRoomService.deleteChatRoom(accountChatRoom);
        return createSendMessage(ChatMessageRequestDto.of(chatRoom.getChatRoomId(), account.getAccountId(),
                account.getDisplayName() + ChatRoomConstants.EnumChatRoomMessage.enumExitChatRoomMessage.getValue()), null);
    }
}
