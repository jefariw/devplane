package {{packageName}}.item;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The example resource generated with every service. Replace it with your own. */
@Entity
@Table(name = "items")
class Item {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected Item() {
		// for JPA
	}

	Item(String name, Instant createdAt) {
		this.name = name;
		this.createdAt = createdAt;
	}

	Long getId() {
		return id;
	}

	String getName() {
		return name;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

}
