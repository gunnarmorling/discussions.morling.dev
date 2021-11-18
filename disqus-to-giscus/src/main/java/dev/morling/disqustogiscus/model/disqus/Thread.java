package dev.morling.disqustogiscus.model.disqus;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record Thread(
        @JacksonXmlProperty(isAttribute = true, namespace = "dsq",localName="id") long id,
        String title,
        String link,
        String createdAt) {
}
