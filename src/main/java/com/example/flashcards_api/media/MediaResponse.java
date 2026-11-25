package com.example.flashcards_api.media;

/**
 * Resposta do upload de mídia.
 *
 * @param id           identificador
 * @param url          caminho para baixar o arquivo ({@code /api/v1/media/{id}})
 * @param contentType  tipo detectado
 * @param size         tamanho em bytes
 * @param originalName nome original sanitizado
 */
public record MediaResponse(Long id, String url, String contentType, long size, String originalName) {

    /**
     * Converte a entidade em resposta.
     *
     * @param media entidade
     * @return resposta
     */
    public static MediaResponse from(Media media) {
        return new MediaResponse(media.getId(), "/api/v1/media/" + media.getId(), media.getContentType(),
                media.getSizeBytes(), media.getOriginalName());
    }
}
