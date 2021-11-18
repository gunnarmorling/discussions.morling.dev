package dev.morling.disqustogiscus.loader;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import dev.morling.disqustogiscus.model.disqus.Disqus;
import dev.morling.disqustogiscus.model.disqus.Post;
import dev.morling.disqustogiscus.model.disqus.Thread;

public class DisqusCommentLoader {

    public static LinkedHashMap<Thread, LinkedHashMap<Long, Post>> loadComments(File disqusXml) {
        Disqus disqus = loadFile(disqusXml);

        var threadsById = disqus.threads()
                .stream()
                .collect(Collectors.toMap(Thread::id, Function.identity()));

        var postsByThread = disqus.posts().stream()
                .map(p -> {
                    Thread thread = threadsById.get(p.thread().id());
                    if (!thread.title().endsWith("- Gunnar Morling")) {
                        thread = new Thread(thread.id(), thread.title() + " - Gunnar Morling", thread.link(), thread.createdAt());
                    }
                    return new Post(
                            p.id(),
                            thread,
                            p.author(),
                            p.message(),
                            p.createdAt(),
                            p.isSpam(),
                            p.parent());
                })
                .collect(Collectors.groupingBy(p -> p.thread()));

        LinkedHashMap<Thread, LinkedHashMap<Long, Post>> threads = new LinkedHashMap<>();

        postsByThread.entrySet()
            .stream()
            .sorted((t1, t2) -> t1.getKey().createdAt().compareTo(t2.getKey().createdAt()))
            .forEach(thread -> {
                LinkedHashMap<Long, Post> posts = new LinkedHashMap<>();
                for (Post post : thread.getValue()) {
                    posts.put(post.id(), post);
                }

                threads.put(thread.getKey(), posts);
            });

//        for (Entry<Thread, LinkedHashMap<Long, Post>> thread : threads.entrySet()) {
//            System.out.println(thread.getKey().title());
//            for (Entry<Long, Post> post : thread.getValue().entrySet()) {
//                System.out.println("  " + post.getValue().author() + " " + post.getValue().message());
//            }
//        }

        return threads;
    }

    private static Disqus loadFile(File disqusXml) {
        XmlMapper mapper = new XmlMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            return mapper.readValue(disqusXml, Disqus.class);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
