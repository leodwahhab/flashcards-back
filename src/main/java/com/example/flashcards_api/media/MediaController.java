package com.example.flashcards_api.media;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Endpoints de mídia ({@code /api/v1/media}). Apenas o dono acessa seus arquivos.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Envia uma imagem ou áudio.
     *
     * @param file           parte multipart {@code file}
     * @param authentication usuário autenticado
     * @return 201 com os metadados
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaResponse upload(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return MediaResponse.from(mediaService.upload(userId(authentication), file));
    }

    /**
     * Baixa o arquivo (exibição inline) com cache privado de 1 dia e {@code nosniff}.
     *
     * @param id             id da mídia
     * @param authentication usuário autenticado
     * @return conteúdo do arquivo
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication authentication) {
        MediaService.StoredMedia stored = mediaService.load(userId(authentication), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.media().getContentType()))
                .contentLength(stored.media().getSizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(stored.media().getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(stored.path()));
    }

    /**
     * Remove a mídia (linha e arquivo).
     *
     * @param id             id da mídia
     * @param authentication usuário autenticado
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        mediaService.delete(userId(authentication), id);
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(String.valueOf(authentication.getPrincipal()));
    }
}
