package dev.morling.disqustogiscus.model.disqus;

import java.util.OptionalLong;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public record Post(
        @JacksonXmlProperty(isAttribute = true, namespace = "dsq",localName="id") long id,
        Thread thread,
        Author author,
        String message,
        String createdAt,
        boolean isSpam,
        Parent parent) {

    public OptionalLong parentId() {
        return parent != null ? OptionalLong.of(parent.id()) : OptionalLong.empty();
    }
}
