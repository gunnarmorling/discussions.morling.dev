package dev.morling.disqustogiscus.command;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.RequestImpl;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClientBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "get-categories")
public class GetCategoriesCommand implements Runnable {

    private static final String GITHUB_GRAPHQL_API = "https://api.github.com/graphql";

    @Option(names = "--access-token", description = "GitHub access token")
    String accessToken;

    @Option(names = "--owner", description = "GitHub repository owner")
    String owner;

    @Option(names = "--name", description = "GitHub repository name")
    String name;

    @Override
    public void run() {
        try(DynamicGraphQLClient client = DynamicGraphQLClientBuilder.newBuilder()
                .header("Authorization", "bearer " + accessToken)
                .url(GITHUB_GRAPHQL_API)
                .build()) {

            String request = """
                    query {
                      repository(owner:"%s", name:"%s") {
                        discussionCategories(first: 10) {
                          nodes {
                            id,
                            name,
                            description
                          }
                        }
                      }
                    }
                    """.formatted(owner, name);
            Response response = client.executeSync(new RequestImpl(request));

            System.out.println(request);

            System.out.println("ID | NAME | DESCRIPTION");
            JsonArray categories = response.getData()
                    .getJsonObject("repository")
                    .getJsonObject("discussionCategories")
                    .getJsonArray("nodes");

            for (JsonValue jsonValue : categories) {
                JsonObject category = jsonValue.asJsonObject();
                System.out.println(category.get("id") + " " + category.get("name") + " " + category.get("description"));
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
