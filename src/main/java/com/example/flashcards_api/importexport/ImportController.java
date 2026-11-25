package com.example.flashcards_api.importexport;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Endpoints de importação ({@code /api/v1/import}).
 */
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final CsvImportService csvImportService;
    private final BackupImportService backupImportService;

    /**
     * Importa notes de um CSV (multipart).
     *
     * @param file           arquivo CSV (UTF-8)
     * @param deckId         baralho de destino (opcional; padrão: baralho padrão do usuário)
     * @param noteType       tipo padrão dos notes (padrão {@code BASIC})
     * @param delimiter      {@code ,} {@code ;} {@code tab} ou {@code auto} (padrão)
     * @param hasHeader      se há cabeçalho (padrão: detectar)
     * @param skipInvalid    ignora linhas inválidas em vez de abortar a importação
     * @param authentication usuário autenticado
     * @return contagens e relatório de erros
     * @throws IOException se o arquivo não puder ser lido
     */
    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportService.Result importCsv(@RequestParam("file") MultipartFile file,
                                             @RequestParam(required = false) Long deckId,
                                             @RequestParam(required = false) String noteType,
                                             @RequestParam(required = false) String delimiter,
                                             @RequestParam(required = false) Boolean hasHeader,
                                             @RequestParam(defaultValue = "false") boolean skipInvalid,
                                             Authentication authentication) throws IOException {
        return csvImportService.importCsv(userId(authentication), file.getBytes(),
                new CsvImportService.Options(deckId, noteType, delimiter, hasHeader, skipInvalid));
    }

    /**
     * Restaura um backup JSON (corpo da requisição, lido por streaming) em uma conta sem notes nem cards.
     *
     * @param request        requisição (o corpo é o JSON gerado por {@code GET /export/backup})
     * @param authentication usuário autenticado
     * @return quantidades restauradas
     * @throws IOException se o corpo não puder ser lido
     */
    @PostMapping(value = "/backup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BackupImportService.Result importBackup(HttpServletRequest request, Authentication authentication)
            throws IOException {
        return backupImportService.restore(userId(authentication), request.getInputStream());
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(String.valueOf(authentication.getPrincipal()));
    }
}
