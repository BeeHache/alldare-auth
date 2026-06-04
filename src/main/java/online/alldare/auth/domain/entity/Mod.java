package online.alldare.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "mods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mod {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;
}