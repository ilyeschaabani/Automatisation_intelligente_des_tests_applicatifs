package com.pfe.platform.apiscannerservice.aicode.service;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.model.CodeChunk;
import com.pfe.platform.apiscannerservice.aicode.model.IngestedFile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CodeChunkerService {

    private final AiCodeAnalyzerProperties props;

    public CodeChunkerService(AiCodeAnalyzerProperties props) {
        this.props = props;
    }

    public List<CodeChunk> chunk(List<IngestedFile> files) {
        List<CodeChunk> out = new ArrayList<>();
        if (files == null || files.isEmpty()) return out;

        int max = props.getMaxChunkChars();
        for (IngestedFile f : files) {
            String text = f.content() != null ? f.content() : "";
            String filePath = String.valueOf(f.path());
            if (text.length() <= max) {
                out.add(new CodeChunk(filePath + "#0", filePath, 0, text));
            } else {
                int idx = 0;
                int chunkIndex = 0;
                while (idx < text.length()) {
                    int end = Math.min(text.length(), idx + max);
                    String part = text.substring(idx, end);
                    out.add(new CodeChunk(filePath + "#" + chunkIndex, filePath, chunkIndex, part));
                    idx = end;
                    chunkIndex++;
                }
            }
        }
        return out;
    }
}

