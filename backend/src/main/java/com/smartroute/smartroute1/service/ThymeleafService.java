package com.smartroute.smartroute1.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service interface for processing Thymeleaf templates and generating HTML content.
 * Implementations of this interface are responsible for rendering a Thymeleaf
 * template with the provided dynamic variables.
 *
 */

@Service
public interface ThymeleafService {

    /**
     * Generates rendered content from a Thymeleaf template.
     *
     * @param s the name or path of the Thymeleaf template to render
     * @param variables a map containing key-value pairs to be substituted in the template
     * @return the rendered content as a {@link String}
     */
    String createContent(String s, Map<String, Object> variables);
}
