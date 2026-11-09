package com.techblog.platform.repository;
import com.techblog.platform.entity.Category;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface CategoryRepository extends JpaRepository<Category,Long> {
 List<Category> findAllByOrderByIdAsc();
 boolean existsByName(String name);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<Category> findByName(String name);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Category c where c.id = :id")
 Optional<Category> lockById(Long id);
}
