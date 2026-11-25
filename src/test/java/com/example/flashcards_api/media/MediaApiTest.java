package com.example.flashcards_api.media;

import com.example.flashcards_api.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras de mídia: tipos verificados pelos magic bytes, limite de tamanho, nome de arquivo malicioso, posse e
 * exclusão. Usa um diretório temporário e um limite pequeno (2 KB).
 */
class MediaApiTest extends AbstractApiIntegrationTest {

    private static final Path DIR;

    static {
        try {
            DIR = Files.createTempDirectory("flashcards-media-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.media.dir", DIR::toString);
        registry.add("app.media.max-bytes", () -> "2048");
    }

    @Test
    void deletingTheAccountRemovesTheUsersMediaFiles() throws Exception {
        TestUser user = registerAndLogin();
        assertThat(upload(user, "cat.png", "image/png", png(50)).getResponse().getStatus()).isEqualTo(201);
        Path userDir = DIR.resolve(String.valueOf(user.userId()));
        assertThat(userDir).isDirectory();

        mvc.perform(authed(user, MockMvcRequestBuilders.delete("/api/v1/account"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(userDir).doesNotExist();
    }

    private static byte[] png(int size) {
        byte[] data = new byte[size];
        byte[] magic = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(magic, 0, data, 0, magic.length);
        return data;
    }

    private MvcResult upload(TestUser user, String name, String contentType, byte[] content) throws Exception {
        MockMultipartHttpServletRequestBuilder req = MockMvcRequestBuilders.multipart("/api/v1/media")
                .file(new MockMultipartFile("file", name, contentType, content));
        return mvc.perform(authed(user, req)).andReturn();
    }

    @Test
    void uploadsAndDownloadsWithSecurityHeaders() throws Exception {
        TestUser user = registerAndLogin();
        byte[] content = png(100);
        MvcResult result = upload(user, "cat.png", "image/png", content);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var body = read(result);
        long id = body.get("id").asLong();
        assertThat(body.get("url").asText()).isEqualTo("/api/v1/media/" + id);
        assertThat(body.get("contentType").asText()).isEqualTo("image/png");
        assertThat(body.get("size").asInt()).isEqualTo(100);
        assertThat(body.get("originalName").asText()).isEqualTo("cat.png");

        try (Stream<Path> files = Files.list(DIR.resolve(String.valueOf(user.userId())))) {
            assertThat(files.toList()).anyMatch(p -> p.getFileName().toString().endsWith(".png"));
        }

        MvcResult download = mvc.perform(getAs(user, "/api/v1/media/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "private, max-age=86400"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void rejectsSpoofedTypes() throws Exception {
        TestUser user = registerAndLogin();
        // HTML/script declarado como imagem
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThat(upload(user, "x.png", "image/png", html).getResponse().getStatus()).isEqualTo(422);
        // PNG real declarado como JPEG
        assertThat(upload(user, "x.jpg", "image/jpeg", png(50)).getResponse().getStatus()).isEqualTo(422);
        // tipo fora da lista, mesmo com conteúdo reconhecível de texto/SVG
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8);
        assertThat(upload(user, "x.svg", "image/svg+xml", svg).getResponse().getStatus()).isEqualTo(422);
        // arquivo vazio
        assertThat(upload(user, "x.png", "image/png", new byte[0]).getResponse().getStatus()).isEqualTo(422);
        // declarado octet-stream, mas conteúdo PNG válido: aceito pelo tipo real
        assertThat(upload(user, "x.bin", "application/octet-stream", png(50)).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void rejectsFilesOverTheLimit() throws Exception {
        TestUser user = registerAndLogin();
        MvcResult result = upload(user, "big.png", "image/png", png(2049));
        assertThat(result.getResponse().getStatus()).isEqualTo(413);
        assertThat(result.getResponse().getContentType()).contains("problem+json");
        assertThat(upload(user, "ok.png", "image/png", png(2048)).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void neverUsesTheClientFilenameAsPath() throws Exception {
        TestUser user = registerAndLogin();
        MvcResult result = upload(user, "../../../evil.png", "image/png", png(40));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(read(result).get("originalName").asText()).isEqualTo("evil.png");

        MvcResult windows = upload(user, "..\\..\\evil2.png", "image/png", png(40));
        assertThat(read(windows).get("originalName").asText()).isEqualTo("evil2.png");

        assertThat(DIR.resolveSibling("evil.png")).doesNotExist();
        assertThat(DIR.resolve("evil.png")).doesNotExist();
        try (Stream<Path> files = Files.list(DIR.resolve(String.valueOf(user.userId())))) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .allMatch(n -> n.matches("[0-9a-f-]{36}\\.png"));
        }
    }

    @Test
    void sanitizesOddNames() {
        assertThat(MediaService.sanitizeName(null)).isEqualTo("arquivo");
        assertThat(MediaService.sanitizeName("..")).isEqualTo("arquivo");
        assertThat(MediaService.sanitizeName("a/b/c.png")).isEqualTo("c.png");
        assertThat(MediaService.sanitizeName("x\u0000y\n.png")).isEqualTo("xy.png");
        assertThat(MediaService.sanitizeName("a".repeat(300)).length()).isEqualTo(255);
    }

    @Test
    void onlyOwnerCanReadOrDelete() throws Exception {
        TestUser owner = registerAndLogin();
        TestUser other = registerAndLogin();
        long id = read(upload(owner, "a.png", "image/png", png(30))).get("id").asLong();

        mvc.perform(getAs(other, "/api/v1/media/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(deleteAs(other, "/api/v1/media/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(getAs(owner, "/api/v1/media/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void deleteRemovesFileAndRowAndToleratesMissingFile() throws Exception {
        TestUser user = registerAndLogin();
        long id = read(upload(user, "a.png", "image/png", png(30))).get("id").asLong();
        Path userDir = DIR.resolve(String.valueOf(user.userId()));
        long before;
        try (Stream<Path> files = Files.list(userDir)) {
            before = files.count();
        }
        assertThat(before).isEqualTo(1);

        mvc.perform(deleteAs(user, "/api/v1/media/{id}", id)).andExpect(status().isNoContent());
        try (Stream<Path> files = Files.list(userDir)) {
            assertThat(files.count()).isZero();
        }
        mvc.perform(getAs(user, "/api/v1/media/{id}", id)).andExpect(status().isNotFound());

        // arquivo já removido do disco: excluir continua funcionando
        long second = read(upload(user, "b.png", "image/png", png(30))).get("id").asLong();
        try (Stream<Path> files = Files.list(userDir)) {
            for (Path p : files.toList()) {
                Files.delete(p);
            }
        }
        mvc.perform(getAs(user, "/api/v1/media/{id}", second)).andExpect(status().isNotFound());
        mvc.perform(deleteAs(user, "/api/v1/media/{id}", second)).andExpect(status().isNoContent());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/media/1")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png(10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void detectsAllAllowedFormatsByMagicBytes() {
        assertThat(detect(png(20))).isEqualTo("image/png");
        assertThat(detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0))).isEqualTo("image/jpeg");
        assertThat(detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEqualTo("image/gif");
        assertThat(detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo("image/webp");
        assertThat(detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo("audio/wav");
        assertThat(detect("OggS\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo("audio/ogg");
        assertThat(detect("ID3\3\0\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo("audio/mpeg");
        assertThat(detect(bytes(0xFF, 0xFB, 0x90, 0x00, 0, 0, 0, 0))).isEqualTo("audio/mpeg");
        assertThat(detect("\0\0\0\u0018ftypM4A \0\0\0\0".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo("audio/mp4");
        assertThat(MediaTypeDetector.detect("hello world".getBytes(StandardCharsets.US_ASCII), 11)).isEmpty();
    }

    private static String detect(byte[] data) {
        byte[] header = Arrays.copyOf(data, Math.min(data.length, MediaTypeDetector.HEADER_SIZE));
        return MediaTypeDetector.detect(header, header.length).orElse("none");
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
