package ro.unibuc.prodeng.e2e.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ro.unibuc.prodeng.response.TeamResponse;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

public class FolderSteps {

    private static final String BASE_URL = "http://localhost:8080";

    private final RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ResponseEntity<String> latestResponse;
    private String authToken;
    private String createdTeamId;

    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authToken != null) {
            headers.setBearerAuth(authToken);
        }
        return headers;
    }

    @Given("the admin logs in with email {string} and password {string}")
    public void adminLogsIn(String email, String password) throws Exception {
        String loginJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(loginJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(BASE_URL + "/api/users/login", entity, String.class);
        
        Map<String, String> responseBody = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, String>>() {});
        this.authToken = responseBody.get("token");
    }

    @When("the admin retrieves all folders for team {string}")
    public void retrieveFoldersForTeam(String teamId) {
        String actualTeamId = "created_team".equals(teamId) ? createdTeamId : teamId;
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders());
        try {
            latestResponse = restTemplate.exchange(
                    BASE_URL + "/api/folders/" + actualTeamId,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
        } catch (HttpClientErrorException e) {
            latestResponse = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @When("the admin creates a root folder named {string} in team {string}")
    public void createRootFolder(String folderName, String teamId) {
        String actualTeamId = "created_team".equals(teamId) ? createdTeamId : teamId;
        String requestJson = String.format("{\"name\":\"%s\", \"teamId\":\"%s\", \"parentFolderId\":null}", folderName, actualTeamId);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, getAuthHeaders());

        try {
            latestResponse = restTemplate.postForEntity(BASE_URL + "/api/folders", entity, String.class);
        } catch (HttpClientErrorException e) {
            latestResponse = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @When("the admin creates a team named {string} with description {string}")
    public void createTeam(String teamName, String description) throws Exception {
        String requestJson = String.format("{\"name\":\"%s\", \"description\":\"%s\"}", teamName, description);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, getAuthHeaders());

        try {
            latestResponse = restTemplate.postForEntity(BASE_URL + "/api/teams", entity, String.class);
            // Store the created team ID for later use
            TeamResponse responseBody = objectMapper.readValue(latestResponse.getBody(), TeamResponse.class);
            this.createdTeamId = responseBody.id();
        } catch (HttpClientErrorException e) {
            latestResponse = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Then("the response status code is {int}")
    public void verifyStatusCode(int expectedStatusCode) {
        assertThat("Status code is incorrect", latestResponse.getStatusCode().value(), is(expectedStatusCode));
    }

    @Then("the client can see at least {int} folder")
    public void verifyFolderCount(int minCount) throws Exception {
        List<Object> folders = objectMapper.readValue(latestResponse.getBody(), new TypeReference<List<Object>>() {});
        assertThat("Folder count is less than expected", folders.size(), greaterThanOrEqualTo(minCount));
    }
}