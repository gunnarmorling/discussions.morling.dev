package dev.morling.disqustogiscus.command;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import dev.morling.disqustogiscus.loader.DisqusCommentLoader;
import dev.morling.disqustogiscus.model.disqus.Post;
import dev.morling.disqustogiscus.model.disqus.Thread;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.RequestImpl;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClientBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "import")
public class ImportCommand implements Runnable {

    private static final String GITHUB_GRAPHQL_API = "https://api.github.com/graphql";

    @Option(names = "--access-token", description = "GitHub access token", required = true)
    String accessToken;

    @Option(names = "--file", description = "File exported from Disqus", required = true)
    File disqusFile;

    @Option(names = "--owner", description = "GitHub repository owner", required = true)
    String owner;

    @Option(names = "--name", description = "GitHub repository name", required = true)
    String name;

    @Option(names = "--category", description = "GitHub discussion category to import to", defaultValue = "Announcements")
    String category;

    @Option(names = "--start-at", description = "Timestamp of first thread to import")
    String startAt;

    @Override
    public void run() {
        try {

            LinkedHashMap<Thread, LinkedHashMap<Long, Post>> posts = DisqusCommentLoader.loadComments(disqusFile);

            String getRepoAndCategoryId = """
                    query {
                      repository(owner:"%s", name:"%s") {
                        discussionCategories(first: 10) {
                          nodes {
                            id,
                            name
                          }
                        },
                        id
                      }
                    }
                    """.formatted(owner, name);

            DynamicGraphQLClient client = DynamicGraphQLClientBuilder.newBuilder()
                    .header("Authorization", "bearer " + accessToken)
                    .url(GITHUB_GRAPHQL_API)
                    .build();

            Response response = client.executeSync(new RequestImpl(getRepoAndCategoryId));
            JsonObject repo = response.getData().getJsonObject("repository");
            String repositoryId = repo.getString("id");
            String categoryId = getCategoryId(repo, category);

            int i = 0;

            for (Entry<Thread, LinkedHashMap<Long, Post>> thread : posts.entrySet()) {
                if (startAt != null) {
                    if (startAt.compareTo(thread.getKey().createdAt()) > 0) {
                        System.out.println("Skipping thread " + thread.getKey().title() + " created at " + thread.getKey().createdAt());
                        continue;
                    }
                }

//                if (i > 4) {
//                    break;
//                }

                String createDiscussionRequest = """
                        mutation {
                          createDiscussion(input: {
                            repositoryId: "%s",
                            categoryId: "%s",
                            body: "%s",
                            title: "%s"
                          }) {

                            discussion {
                              id
                            }
                          }
                        }
                        """.formatted(repositoryId, categoryId, thread.getKey().link(), thread.getKey().title());


                Response createDiscussionResponse = client.executeSync(new RequestImpl(createDiscussionRequest));

                if (createDiscussionResponse.getErrors() != null && !createDiscussionResponse.getErrors().isEmpty()) {
                    System.out.println("Create Discussion request: " + createDiscussionRequest);
                    System.out.println("Create Discussion response: " + createDiscussionResponse);
                }

                String discussionId = createDiscussionResponse.getData()
                    .getJsonObject("createDiscussion")
                    .getJsonObject("discussion")
                    .getString("id");

                System.out.println("Imported thread " + thread.getKey().title() + " created at " + thread.getKey().createdAt());

                Map<Long, String> mappedCommentIds = new HashMap<>();

                for (Post post : thread.getValue().values()) {
                    if (post.isSpam()) {
                        System.out.println("Ignored spam comment by " + post.author().name() + " posted at " + post.createdAt());
                        continue;
                    }

                    String parentId = getParentId(post, thread.getValue(), mappedCommentIds);

                    String addCommentRequest = """
                            mutation {
                              addDiscussionComment(input: {
                                  discussionId: "%s",
                                  replyToId: %s,
                                body: "%s"
                              }) {

                                comment {
                                  id
                                }
                              }
                            }
                            """.formatted(discussionId, parentId, convertMessage(post));

                    String commentId = addComment(client, addCommentRequest);
                    System.out.println("Imported comment by " + post.author().name() + " posted at " + post.createdAt());

                    mappedCommentIds.put(post.id(), commentId);

                    java.lang.Thread.sleep(500);
                    i++;
                }
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String addComment(DynamicGraphQLClient client, String addCommentRequest)
            throws ExecutionException, InterruptedException {

        Response addCommentResponse = client.executeSync(new RequestImpl(addCommentRequest));

            if (addCommentResponse.getErrors() != null && !addCommentResponse.getErrors().isEmpty()) {
                System.out.println("Create Comment request: " + addCommentRequest);
                System.out.println("Create Comment response: " + addCommentResponse);
            }

            return addCommentResponse.getData()
                    .getJsonObject("addDiscussionComment")
                    .getJsonObject("comment")
                    .getString("id");
    }

    private String getParentId(Post post, LinkedHashMap<Long, Post> allPostsOfThread, Map<Long, String> mappedCommentIds) {
        OptionalLong parentId = post.parentId();

        if (!parentId.isPresent()) {
            return null;
        }

        Post parent = null;
        while (parentId.isPresent()) {
            parent = allPostsOfThread.get(parentId.getAsLong());
            parentId = parent.parentId();
        }

        return "\"" + mappedCommentIds.get(parent.id()) + "\"";
    }

    private String convertMessage(Post post) {
        String message = post.message();

        message = message.trim()
                .replaceAll("\"", "\\\\\"");

        message = """
                _**%s** wrote at %s (comment imported from Disqus):_

                %s
                """.formatted(post.author().name(), post.createdAt(), message);

        return message;
    }

    private String getCategoryId(JsonObject repo, String categoryName) {
        JsonArray categories = repo.getJsonObject("discussionCategories")
                .getJsonArray("nodes");

        for (JsonValue jsonValue : categories) {
            JsonObject category = jsonValue.asJsonObject();
            if (category.getString("name").equals(categoryName)) {
                return category.getString("id");
            }
        }

        throw new IllegalArgumentException("Found no category with name " + categoryName);
    }
}
