package com.chat.back;

import com.chat.back.certificate.CertificateService;
import com.chat.back.entity.*;
import com.chat.back.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatBackApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    // ── Certificate service ────────────────────────────────────────────────────

    @Test
    void caCertificateIsGenerated() {
        assertThat(certificateService.getCaCertificate()).isNotNull();
        assertThat(certificateService.getCaCertificatePem()).contains("BEGIN CERTIFICATE");
    }

    @Test
    void intermediateCertificateIsGeneratedAndSignedByCa() throws Exception {
        var intermediate = certificateService.getIntermediateCertificate();
        assertThat(intermediate).isNotNull();
        // Verify intermediate is signed by the CA
        intermediate.verify(certificateService.getCaCertificate().getPublicKey());
    }

    @Test
    void getCaCertificateEndpointReturnsPem() throws Exception {
        mockMvc.perform(get("/api/certificates/ca"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN CERTIFICATE")));
    }

    @Test
    void getIntermediateCertificateEndpointReturnsPem() throws Exception {
        mockMvc.perform(get("/api/certificates/intermediate"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN CERTIFICATE")));
    }

    // ── Entities / repositories ────────────────────────────────────────────────

    @Test
    void userCanBeSavedAndRetrievedByPhoneNumber() {
        User user = User.builder()
                .phoneNumber("+15555550100")
                .displayName("Alice")
                .about("Hey there!")
                .build();
        userRepository.save(user);

        assertThat(userRepository.findByPhoneNumber("+15555550100")).isPresent()
                .get()
                .extracting(User::getDisplayName)
                .isEqualTo("Alice");
    }

    @Test
    void directConversationCanBeCreated() {
        Conversation conv = Conversation.builder()
                .type(ConversationType.DIRECT)
                .build();
        conversationRepository.save(conv);
        assertThat(conversationRepository.findByType(ConversationType.DIRECT)).isNotEmpty();
    }

    @Test
    void messageCanBeSavedAndRetrievedByConversation() {
        User sender = userRepository.save(User.builder()
                .phoneNumber("+15555550200")
                .displayName("Bob")
                .build());

        Conversation conv = conversationRepository.save(Conversation.builder()
                .type(ConversationType.DIRECT)
                .build());

        Message msg = messageRepository.save(Message.builder()
                .conversation(conv)
                .sender(sender)
                .messageType(MessageType.TEXT)
                .content("Hello World")
                .build());

        var messages = messageRepository.findByConversationIdOrderBySentAtAsc(conv.getId());
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello World");
    }
}
