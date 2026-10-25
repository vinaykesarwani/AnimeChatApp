package com.anime.backend.controller;

import com.anime.backend.dto.ChatMessageDto;
import com.anime.backend.dto.DeleteMessageRequest;
import com.anime.backend.dto.EditMessageRequest;
import com.anime.backend.dto.MessageEvent;
import com.anime.backend.entity.DiscussionRoom;
import com.anime.backend.entity.Message;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.DiscussionRoomRepository;
import com.anime.backend.repository.MessageRepository;
import com.anime.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatWebSocketControllerTest {
    private MessageRepository messages;
    private DiscussionRoomRepository rooms;
    private UserRepository users;
    private SimpMessagingTemplate broker;
    private ChatWebSocketController controller;
    private User alice;
    private DiscussionRoom room;

    @BeforeEach
    void setUp() {
        messages = mock(MessageRepository.class);
        rooms = mock(DiscussionRoomRepository.class);
        users = mock(UserRepository.class);
        broker = mock(SimpMessagingTemplate.class);
        controller = new ChatWebSocketController(messages, rooms, users, broker);
        alice = new User();
        alice.setUsername("alice");
        alice.setRole(Role.USER);
        room = new DiscussionRoom();
        room.setId(7L);
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(rooms.findById(7L)).thenReturn(Optional.of(room));
    }

    @Test
    void validReplyIsSavedAndBroadcastToItsRoom() {
        // Arrange: the reply target belongs to this room.
        Message parent = existingMessage();
        when(messages.findById(10L)).thenReturn(Optional.of(parent));
        Message savedMessage = new Message();
        savedMessage.setId(11L);
        when(messages.save(any(Message.class))).thenReturn(savedMessage);
        ChatMessageDto request = sendRequest("  I agree  ");
        request.setReplyToMessageId(10L);
        request.setTempId("draft-1");

        // Act: run the real controller, with storage and STOMP replaced by mocks.
        controller.send(7L, request, () -> "alice");

        // Assert: the event contains the saved message and goes to the correct room.
        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<MessageEvent> event = ArgumentCaptor.forClass(MessageEvent.class);
        verify(messages).save(saved.capture());
        verify(broker).convertAndSend(eq("/topic/chat/7"), event.capture());
        assertEquals("I agree", saved.getValue().getContent());
        assertSame(alice, saved.getValue().getSender());
        assertSame(room, saved.getValue().getDiscussionRoom());
        assertSame(parent, saved.getValue().getReplyTo());
        assertEquals("CREATE", event.getValue().getType());
        assertSame(savedMessage, event.getValue().getMessage());
        assertEquals(11L, event.getValue().getMessage().getId());
        assertEquals("draft-1", event.getValue().getMessage().getTempId());
    }

    @Test
    void crossRoomReplyIsNeitherSavedNorBroadcast() {
        Message parent = existingMessage();
        DiscussionRoom otherRoom = new DiscussionRoom();
        otherRoom.setId(8L);
        parent.setDiscussionRoom(otherRoom);
        when(messages.findById(10L)).thenReturn(Optional.of(parent));
        ChatMessageDto request = sendRequest("reply");
        request.setReplyToMessageId(10L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.send(7L, request, () -> "alice"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(messages, never()).save(any(Message.class));
        verifyNoInteractions(broker);
    }

    @Test
    void anotherUserCannotEditOrDeleteAMessage() {
        Message message = existingMessage();
        when(messages.findById(10L)).thenReturn(Optional.of(message));
        User bob = new User();
        bob.setUsername("bob");
        bob.setRole(Role.USER);
        when(users.findByUsername("bob")).thenReturn(Optional.of(bob));
        DeleteMessageRequest delete = new DeleteMessageRequest();
        delete.setMessageId(10L);

        ResponseStatusException editError = assertThrows(ResponseStatusException.class,
                () -> controller.edit(editRequest("tampered"), () -> "bob"));
        ResponseStatusException deleteError = assertThrows(ResponseStatusException.class,
                () -> controller.delete(delete, () -> "bob"));

        assertEquals(HttpStatus.FORBIDDEN, editError.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, deleteError.getStatusCode());
        assertEquals("original", message.getContent());
        assertFalse(message.isDeleted());
        verify(messages, never()).save(any(Message.class));
        verifyNoInteractions(broker);
    }

    @Test
    void failedSaveDoesNotBroadcastAnUnstoredMessage() {
        when(messages.save(any(Message.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> controller.send(7L, sendRequest("hello"), () -> "alice"));

        verifyNoInteractions(broker);
    }

    private Message existingMessage() {
        Message message = new Message();
        message.setId(10L);
        message.setContent("original");
        message.setSender(alice);
        message.setDiscussionRoom(room);
        return message;
    }

    private ChatMessageDto sendRequest(String content) {
        ChatMessageDto request = new ChatMessageDto();
        request.setContent(content);
        return request;
    }

    private EditMessageRequest editRequest(String content) {
        EditMessageRequest request = new EditMessageRequest();
        request.setMessageId(10L);
        request.setContent(content);
        return request;
    }
}
