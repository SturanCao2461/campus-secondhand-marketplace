package nz.ac.waikato.campusmarketplace.dto;

import nz.ac.waikato.campusmarketplace.entity.Category;

public record CategoryResponse(String code, String nameEn, String nameZh) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getCode(), c.getNameEn(), c.getNameZh());
    }
}
