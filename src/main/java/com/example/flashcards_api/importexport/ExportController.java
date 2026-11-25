package com.example.flashcards_api.importexport;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Endpoints de exportação ({@code /api/v1/export}). As respostas são escritas diretamente no fluxo de saída, em
 * blocos, sem montar o conteúdo inteiro em memória.
 */
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {

    private final CsvExportService csvExportService;
    private final BackupExportService backupExportService;

    /**
     * Exporta notes em CSV UTF-8 (cabeçalho {@code deck,noteType,front,back,tags,imageUrl}).
     *
     * @param deckId         baralho a exportar, com subbaralhos (opcional; padrão: todos)
     * @param authentication usuário autenticado
     * @param response       resposta HTTP
     * @throws IOException se a escrita falhar
     */
    @GetMapping("/csv")
    public void exportCsv(@RequestParam(required = false) Long deckId, Authentication authentication,
                          HttpServletResponse response) throws IOException {
        CsvExportService.Plan plan = csvExportService.plan(userId(authentication), deckId);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flashcards.csv\"");
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        csvExportService.write(plan, writer);
        writer.flush();
    }

    /**
     * Exporta todos os dados do usuário em JSON versionado (restaurável com {@code POST /import/backup}).
     *
     * @param authentication usuário autenticado
     * @param response       resposta HTTP
     * @throws IOException se a escrita falhar
     */
    @GetMapping("/backup")
    public void exportBackup(Authentication authentication, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flashcards-backup.json\"");
        backupExportService.write(userId(authentication), response.getOutputStream());
        response.getOutputStream().flush();
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(String.valueOf(authentication.getPrincipal()));
    }
}
