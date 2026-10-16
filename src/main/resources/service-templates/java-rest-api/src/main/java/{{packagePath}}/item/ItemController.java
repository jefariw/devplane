package {{packageName}}.item;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The example REST resource. It talks to the repository directly; add a service
 * class between them once the resource has business rules of its own.
 */
@RestController
@RequestMapping("/api/v1/items")
class ItemController {

	private final ItemRepository items;

	ItemController(ItemRepository items) {
		this.items = items;
	}

	@PostMapping
	ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemRequest request) {
		// PostgreSQL stores microseconds, so drop finer precision to match what's read back.
		Item item = items.save(new Item(request.name(), Instant.now().truncatedTo(ChronoUnit.MICROS)));
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
			.path("/{id}")
			.buildAndExpand(item.getId())
			.toUri();
		return ResponseEntity.created(location).body(ItemResponse.from(item));
	}

	/** Every item, in creation order. */
	@GetMapping
	List<ItemResponse> list() {
		return items.findAll(Sort.by("id")).stream().map(ItemResponse::from).toList();
	}

	@GetMapping("/{id}")
	ItemResponse get(@PathVariable long id) {
		return items.findById(id).map(ItemResponse::from).orElseThrow(() -> new ItemNotFoundException(id));
	}

}
