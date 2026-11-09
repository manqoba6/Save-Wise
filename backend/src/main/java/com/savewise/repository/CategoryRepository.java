package com.savewise.repository;

import com.savewise.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByTypeOrderByNameAsc(String type);
    List<Category> findAllByOrderByTypeAscNameAsc();
    boolean existsByNameIgnoreCaseAndType(String name, String type);
}
