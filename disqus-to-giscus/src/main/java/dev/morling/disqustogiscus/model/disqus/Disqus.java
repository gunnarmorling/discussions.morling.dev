package dev.morling.disqustogiscus.model.disqus;

import java.util.List;

public record Disqus(Category category, List<Thread> threads, List<Post> posts) {
}
