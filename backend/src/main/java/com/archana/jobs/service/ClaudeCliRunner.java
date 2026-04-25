package com.archana.jobs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Dev-mode Claude harness: shells out to `claude -p --output-format json`
 * instead of calling the Anthropic API directly. Uses the user's Claude Code
 * subscription auth on the host machine. Swap this for the SDK-based caller
 * when moving to API billing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeCliRunner {

    private static final long TIMEOUT_SECONDS = 300;

    private final ObjectMapper objectMapper;

    /**
     * Runs a prompt through `claude -p --model sonnet` and parses the model's
     * text response as JSON into the requested type.
     */
    public <T> T runStructured(String prompt, Class<T> resultType) {
        return runStructured(prompt, resultType, "sonnet");
    }

    public <T> T runStructured(String prompt, Class<T> resultType, String model) {
        String resultText = runRaw(prompt, model);
        String json = stripCodeFences(resultText.trim());
        try {
            return objectMapper.readValue(json, resultType);
        } catch (Exception e) {
            log.error("Failed to parse Claude CLI response as {}: {}",
                    resultType.getSimpleName(), json);
            throw new RuntimeException("Claude returned non-JSON / unparseable response", e);
        }
    }

    public String runRaw(String prompt) {
        return runRaw(prompt, "sonnet");
    }

    /**
     * Runs `claude -p --output-format json --model {model}`, returns the
     * model's `.result` text verbatim. No JSON parsing of the model output.
     */
    public String runRaw(String prompt, String model) {
        // --max-turns 1 keeps it strictly single-turn (no agentic loops on big prompts)
        List<String> command = List.of(
                "claude", "-p",
                "--output-format", "json",
                "--model", model,
                "--max-turns", "1");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not invoke `claude` CLI. Ensure Claude Code is installed and on PATH.", e);
        }

        try (OutputStream stdin = proc.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            proc.destroyForcibly();
            throw new RuntimeException("Failed to write prompt to claude CLI", e);
        }

        String stdout, stderr;
        try {
            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                proc.destroyForcibly();
                throw new RuntimeException("claude CLI timed out after " + TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            throw new RuntimeException("Interrupted waiting for claude CLI", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read claude CLI output", e);
        }

        if (proc.exitValue() != 0) {
            throw new RuntimeException(
                    "claude CLI exited " + proc.exitValue() + ": " + stderr);
        }

        try {
            JsonNode envelope = objectMapper.readTree(stdout);
            if (envelope.path("is_error").asBoolean(false)) {
                throw new RuntimeException(
                        "claude CLI returned an error: " + envelope.path("result").asText("(no message)"));
            }
            String result = envelope.path("result").asText(null);
            if (result == null) {
                throw new RuntimeException("claude CLI envelope had no `result` field: " + stdout);
            }
            log.info("claude CLI: {}ms, {} input + {} output tokens",
                    envelope.path("duration_ms").asLong(),
                    envelope.path("usage").path("input_tokens").asLong(),
                    envelope.path("usage").path("output_tokens").asLong());
            return result;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse claude CLI envelope: " + stdout, e);
        }
    }

    private String stripCodeFences(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }
}
