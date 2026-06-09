package com.example.flashcards_api;

import com.example.flashcards_api.auth.AuthResponse;
import com.example.flashcards_api.auth.UserRepository;
import com.example.flashcards_api.flashcard.FlashcardRepository;
import com.example.flashcards_api.group.FlashcardGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlashcardsApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository userRepository;
    @Autowired private FlashcardRepository flashcardRepository;
    @Autowired private FlashcardGroupRepository groupRepository;

    private String getToken(String email, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", email, "password", password));
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        if (result.getResponse().getStatus() == 201) {
            return mapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class).token();
        }
        result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class).token();
    }

    @BeforeEach
    void clean() {
        groupRepository.deleteAll();
        flashcardRepository.deleteAll();
        userRepository.deleteAll();
    }

    // === AUTH TESTS ===

    @Test
    void register_returnsToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"test@test.com","password":"123456"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        getToken("dup@test.com", "123456");
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"dup@test.com","password":"123456"}
                            """))
                .andExpect(status().isConflict());
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        getToken("login@test.com", "123456");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"login@test.com","password":"123456"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        getToken("wrong@test.com", "123456");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"wrong@test.com","password":"wrongpass"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/flashcards"))
                .andExpect(status().isUnauthorized());
    }

    // === FLASHCARD CRUD TESTS ===

    @Test
    void createFlashcard_textOnly() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"What is Java?","answer":"A programming language"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.textContent").value("What is Java?"))
                .andExpect(jsonPath("$.dominanceLevel").value("low"))
                .andExpect(jsonPath("$.lastShownDate").isEmpty())
                .andExpect(jsonPath("$.attemptsCorrect").value(0))
                .andExpect(jsonPath("$.attemptsIncorrect").value(0));
    }

    @Test
    void createFlashcard_imageOnly() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"imageUrl":"http://img.com/a.png","answer":"Answer"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("http://img.com/a.png"));
    }

    @Test
    void createFlashcard_noAnswer_returns422() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Question"}
                            """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createFlashcard_noContent_returns422() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"answer":"Answer"}
                            """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getFlashcard_notFound_returns404() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(get("/api/v1/flashcards/999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFlashcard_otherUser_returns404() throws Exception {
        String token1 = getToken("user1@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        String token2 = getToken("user2@test.com", "123456");
        mvc.perform(get("/api/v1/flashcards/" + id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFlashcard_success() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(put("/api/v1/flashcards/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"answer":"Updated"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Updated"));
    }

    @Test
    void deleteFlashcard_success() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(delete("/api/v1/flashcards/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // === GROUP TESTS ===

    @Test
    void createGroup_success() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Java Basics"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Java Basics"));
    }

    @Test
    void createGroup_noName_returns422() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {}
                            """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deleteGroup_flashcardsStillExist() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult fcRes = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long fcId = mapper.readTree(fcRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult grpRes = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Group"}
                            """))
                .andReturn();
        Long grpId = mapper.readTree(grpRes.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/groups/" + grpId + "/flashcards/" + fcId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/groups/" + grpId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/flashcards/" + fcId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // === FLASHCARD-GROUP ASSOCIATION TESTS ===

    @Test
    void addFlashcardToGroup_duplicate_returns409() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult fcRes = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long fcId = mapper.readTree(fcRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult grpRes = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Group"}
                            """))
                .andReturn();
        Long grpId = mapper.readTree(grpRes.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/groups/" + grpId + "/flashcards/" + fcId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/groups/" + grpId + "/flashcards/" + fcId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void removeFlashcardFromGroup_success() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult fcRes = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long fcId = mapper.readTree(fcRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult grpRes = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Group"}
                            """))
                .andReturn();
        Long grpId = mapper.readTree(grpRes.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/groups/" + grpId + "/flashcards/" + fcId)
                        .header("Authorization", "Bearer " + token));

        mvc.perform(delete("/api/v1/groups/" + grpId + "/flashcards/" + fcId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // === STUDY SESSION TESTS ===

    @Test
    void study_getFlashcards_returnsOwn() throws Exception {
        String token = getToken("user@test.com", "123456");
        mvc.perform(post("/api/v1/flashcards")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"textContent":"Q","answer":"A"}
                    """));

        mvc.perform(get("/api/v1/study/flashcards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dominanceLevel").value("low"));
    }

    @Test
    void study_getFlashcards_unownedGroup_returns404() throws Exception {
        String token1 = getToken("user1@test.com", "123456");
        MvcResult grpRes = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Group"}
                            """))
                .andReturn();
        Long grpId = mapper.readTree(grpRes.getResponse().getContentAsString()).get("id").asLong();

        String token2 = getToken("user2@test.com", "123456");
        mvc.perform(get("/api/v1/study/flashcards?group_id=" + grpId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    void study_submitCorrect_incrementsCounter() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"correct"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsCorrect").value(1))
                .andExpect(jsonPath("$.attemptsIncorrect").value(0))
                .andExpect(jsonPath("$.lastShownDate").isNotEmpty());
    }

    @Test
    void study_submitIncorrect_incrementsCounter() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"incorrect"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsIncorrect").value(1))
                .andExpect(jsonPath("$.attemptsCorrect").value(0));
    }

    @Test
    void study_threeCorrect_promotesFromLowToMedium() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"result":"correct"}
                        """));
        }

        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"correct"}
                            """))
                .andExpect(jsonPath("$.dominanceLevel").value("medium"))
                .andExpect(jsonPath("$.attemptsCorrect").value(0))
                .andExpect(jsonPath("$.attemptsIncorrect").value(0));
    }

    @Test
    void study_threeCorrectFromHigh_staysHigh() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Promote to high: 3 correct (low→medium) + 3 correct (medium→high)
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"result":"correct"}
                        """));
        }

        // Now 3 more correct while at high
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"result":"correct"}
                        """));
        }
        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"correct"}
                            """))
                .andExpect(jsonPath("$.dominanceLevel").value("high"))
                .andExpect(jsonPath("$.attemptsCorrect").value(0));
    }

    @Test
    void study_threeIncorrect_demotesFromHighToMedium() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Promote to high
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"result":"correct"}
                        """));
        }

        // 3 incorrect → demote to medium
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"result":"incorrect"}
                        """));
        }
        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"incorrect"}
                            """))
                .andExpect(jsonPath("$.dominanceLevel").value("medium"))
                .andExpect(jsonPath("$.attemptsCorrect").value(0))
                .andExpect(jsonPath("$.attemptsIncorrect").value(0));
    }

    @Test
    void study_threeIncorrectFromLow_staysLow() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"result":"incorrect"}
                        """));
        }
        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"incorrect"}
                            """))
                .andExpect(jsonPath("$.dominanceLevel").value("low"))
                .andExpect(jsonPath("$.attemptsIncorrect").value(0));
    }

    @Test
    void study_invalidResult_returns422() throws Exception {
        String token = getToken("user@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"maybe"}
                            """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void study_unownedFlashcard_returns404() throws Exception {
        String token1 = getToken("user1@test.com", "123456");
        MvcResult res = mvc.perform(post("/api/v1/flashcards")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"textContent":"Q","answer":"A"}
                            """))
                .andReturn();
        Long id = mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        String token2 = getToken("user2@test.com", "123456");
        mvc.perform(post("/api/v1/study/flashcards/" + id + "/answer")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"result":"correct"}
                            """))
                .andExpect(status().isNotFound());
    }
}
