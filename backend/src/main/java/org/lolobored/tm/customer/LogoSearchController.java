package org.lolobored.tm.customer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class LogoSearchController {

    private final LogoSearchProperties properties;
    private final CustomerRepository customerRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public LogoSearchController(LogoSearchProperties properties,
                                 CustomerRepository customerRepository) {
        this.properties = properties;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/api/logo-search")
    public List<LogoSearchResult> search(@RequestParam String q) {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Logo search not configured. Set GOOGLE_API_KEY and GOOGLE_CSE_ID.");
        }

        String url = UriComponentsBuilder
                .fromUriString("https://www.googleapis.com/customsearch/v1")
                .queryParam("key", properties.getGoogleApiKey())
                .queryParam("cx", properties.getGoogleCseId())
                .queryParam("q", q + " logo")
                .queryParam("searchType", "image")
                .queryParam("num", 10)
                .toUriString();

        JsonNode response = restTemplate.getForObject(url, JsonNode.class);
        List<LogoSearchResult> results = new ArrayList<>();
        if (response != null && response.has("items")) {
            for (JsonNode item : response.get("items")) {
                String imageUrl = item.get("link").asText();
                String thumbnail = item.has("image") && item.get("image").has("thumbnailLink")
                        ? item.get("image").get("thumbnailLink").asText()
                        : imageUrl;
                results.add(new LogoSearchResult(imageUrl, thumbnail));
            }
        }
        return results;
    }

    @PostMapping("/api/customers/{id}/logo-from-url")
    public void setLogoFromUrl(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String imageUrl = body.get("url");
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            var response = restTemplate.getForEntity(imageUrl, byte[].class);
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString() : "";

            if (!contentType.startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "URL does not point to an image");
            }

            byte[] imageBytes = response.getBody();
            if (imageBytes == null || imageBytes.length > 2 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image too large (max 2MB)");
            }

            customer.setLogo(imageBytes);
            customer.setLogoContentType(contentType);
            customerRepository.save(customer);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to download image from URL");
        }
    }
}
