package xyz.kangnasi.interview.auth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanLoginAndReadCurrentUser() throws Exception {
        sendLoginCode("admin@example.com");
        String token = login("admin@example.com", "123456");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void loginTokenCanBeIssuedWithoutExpiration() throws Exception {
        sendLoginCode("admin@example.com");
        String token = login("admin@example.com", "123456");

        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertFalse(payload.has("exp"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@example.com"));
    }

    @Test
    void sendLoginCodeRequiresValidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/send-login-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
    }

    @Test
    void loginRequiresEmailAndCode() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入邮箱和验证码"));
    }

    @Test
    void wrongCodeIsRejected() throws Exception {
        sendLoginCode("admin@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@example.com",
                                "code", "000000"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("验证码错误或已过期"));
    }

    @Test
    void newEmailLoginCreatesNormalUser() throws Exception {
        sendLoginCode("new-user@example.com");

        String token = login("new-user@example.com", "123456");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("new-user@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void currentUserCanUpdateNickname() throws Exception {
        sendLoginCode("user1@example.com");
        String token = login("user1@example.com", "123456");

        mockMvc.perform(patch("/api/auth/me/nickname")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "新昵称"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));
    }

    @Test
    void updateNicknameRequiresNonBlankNickname() throws Exception {
        sendLoginCode("user1@example.com");
        String token = login("user1@example.com", "123456");

        mockMvc.perform(patch("/api/auth/me/nickname")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("昵称不能为空"));
    }

    @Test
    void repeatedWrongCodesLockLoginTemporarily() throws Exception {
        String email = "locked@example.com";

        for (int i = 0; i < 2; i++) {
            sendLoginCode(email);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", email,
                                    "code", "000000"
                            ))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("验证码错误或已过期"));
        }

        sendLoginCode(email);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "code", "000000"
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请稍后再试"));

        sendLoginCode(email);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "code", "123456"
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请稍后再试"));
    }

    @Test
    void unauthenticatedApiRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/questions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    void normalUserCannotAccessAdminApi() throws Exception {
        sendLoginCode("user1@example.com");
        String token = login("user1@example.com", "123456");

        mockMvc.perform(get("/api/admin/documents").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权限访问"));
    }

    @Test
    void logoutRevokesCurrentToken() throws Exception {
        sendLoginCode("user1@example.com");
        String token = login("user1@example.com", "123456");

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void rootEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service").value("coding-interview-practice-platform-backend"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void reactAdminWebIsServedByBackend() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/index.html"));

        mockMvc.perform(get("/admin/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"root\"></div>")));
    }

    @Test
    void missingStaticResourceReturns404() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    private void sendLoginCode(String email) throws Exception {
        mockMvc.perform(post("/api/auth/send-login-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String login(String email, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "code", code
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.at("/data/token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
