package com.savewise.service;

import com.savewise.dto.CategoryRequest;
import com.savewise.dto.CategoryResponse;
import com.savewise.entity.Category;
import com.savewise.exception.ApiException;
import com.savewise.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(String type) {
        List<Category> found = (type == null || type.isBlank())
                ? repo.findAllByOrderByTypeAscNameAsc()
                : repo.findByTypeOrderByNameAsc(type.toUpperCase());
        return found.stream().map(CategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (repo.existsByNameIgnoreCaseAndType(req.name().trim(), req.type())) {
            throw new ApiException(HttpStatus.CONFLICT, "That category already exists.");
        }
        Category c = repo.save(Category.builder().name(req.name().trim()).type(req.type()).icon(req.icon()).build());
        return toResponse(c);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        c.setName(req.name().trim());
        c.setType(req.type());
        c.setIcon(req.icon());
        return toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ApiException(HttpStatus.NOT_FOUND, "Category not found.");
        repo.deleteById(id);
        repo.flush(); // surface FK violations here so the handler can return 409
    }

    /** Loads a category and checks it is the expected type (INCOME or EXPENSE). */
    public Category requireOfType(Long id, String type) {
        Category c = repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Category does not exist."));
        if (!c.getType().equals(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category must be of type " + type + ".");
        }
        return c;
    }

    static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getIcon());
    }
}
