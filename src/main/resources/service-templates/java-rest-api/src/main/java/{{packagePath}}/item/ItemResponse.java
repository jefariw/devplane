package {{packageName}}.item;

import java.time.Instant;

/** An item as the API returns it. */
record ItemResponse(long id, String name, Instant createdAt) {

	static ItemResponse from(Item item) {
		return new ItemResponse(item.getId(), item.getName(), item.getCreatedAt());
	}

}
