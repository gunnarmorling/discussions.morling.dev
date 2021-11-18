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

@Command(name = "delete-all-discussions")
public class DeleteAllDiscussionsCommand implements Runnable {

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
                        discussions(first: 100) {
                          totalCount
                          nodes {
                            id
                          }
                        }
                      }
                    }
                    """.formatted(owner, name);

            Response response = client.executeSync(new RequestImpl(request));

            JsonObject discussions = response.getData()
                    .getJsonObject("repository")
                    .getJsonObject("discussions");

            int total = discussions.getInt("totalCount");
            JsonArray nodes = discussions.getJsonArray("nodes");

            System.out.println("Deleting " + total + " discussions");

            for (JsonValue jsonValue : nodes) {
                JsonObject discussion = jsonValue.asJsonObject();

                String deleteRequest = """
                        mutation {
                          deleteDiscussion(input: {
                           id: "%s"
                          }) {
                            discussion {
                              id
                              title
                            }
                          }
                        }
                        """.formatted(discussion.getString("id"));

                Response deleteResponse = client.executeSync(new RequestImpl(deleteRequest));
                JsonObject deleted = deleteResponse.getData()
                    .getJsonObject("deleteDiscussion")
                    .getJsonObject("discussion");

                System.out.println("Deleted discussion " + deleted.getString("id") + " - " + deleted.getString("title"));
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
