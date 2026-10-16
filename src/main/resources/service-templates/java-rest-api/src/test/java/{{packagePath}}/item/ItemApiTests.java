package {{packageName}}.item;

import static org.assertj.core.api.Assertions.assertThat;

import {{packageName}}.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ItemApiTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ItemRepository items;

	@BeforeEach
	void clearItems() {
		items.deleteAll();
	}

	@Test
	void createsAnItem() {
		MvcTestResult result = create("""
				{"name": "First item"}
				""");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result.getResponse().getHeader("Location")).matches(".*/api/v1/items/\\d+");
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("First item");
		assertThat(result).bodyJson().hasPath("$.id").hasPath("$.createdAt");
	}

	@Test
	void listsItems() {
		create("""
				{"name": "First item"}
				""");
		create("""
				{"name": "Second item"}
				""");

		assertThat(mvc.get().uri("/api/v1/items")).hasStatusOk()
			.bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("First item", "Second item");
	}

	@Test
	void rejectsAnItemWithoutAName() {
		MvcTestResult result = create("{}");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("name");
		assertThat(items.count()).isZero();
	}

	@Test
	void returns404ForAnUnknownItem() {
		assertThat(mvc.get().uri("/api/v1/items/{id}", Long.MAX_VALUE)).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
	}

	private MvcTestResult create(String json) {
		return mvc.post().uri("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
