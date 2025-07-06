package in.apoorvsahu.removebg.Repositories;

import in.apoorvsahu.removebg.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByClerkId(String clerkId);
    boolean existsByClerkId(String clerkId);
}
