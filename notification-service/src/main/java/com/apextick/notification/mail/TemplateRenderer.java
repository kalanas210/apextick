package com.apextick.notification.mail;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

@Component
public class TemplateRenderer {

    private final ITemplateEngine templateEngine;

    public TemplateRenderer(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(String template, Map<String, Object> model) {
        Context context = new Context(Locale.ENGLISH, model);
        return templateEngine.process(template, context);
    }
}
