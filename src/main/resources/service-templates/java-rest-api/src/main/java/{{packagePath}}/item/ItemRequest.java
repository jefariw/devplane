package {{packageName}}.item;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/items}. */
record ItemRequest(@NotNull @Size(min = 1, max = 100) String name) {

}
