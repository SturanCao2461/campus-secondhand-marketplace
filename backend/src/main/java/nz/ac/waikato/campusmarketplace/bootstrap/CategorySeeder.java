package nz.ac.waikato.campusmarketplace.bootstrap;

import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CategorySeeder {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    private static final List<Category> SEED = List.of(
            seed("BOOKS",       "Books & Textbooks",  "书籍教材",   10),
            seed("ELECTRONICS", "Electronics",        "电子产品",   20),
            seed("FURNITURE",   "Furniture",          "家具",       30),
            seed("CLOTHING",    "Clothing & Bags",    "衣物鞋帽",   40),
            seed("KITCHEN",     "Kitchen & Home",     "厨房家用",   50),
            seed("SPORTS",      "Sports & Outdoors",  "运动器材",   60),
            seed("TICKETS",     "Tickets & Events",   "票务",       70),
            seed("OTHER",       "Other",              "其他",       99)
    );

    private static Category seed(String code, String en, String zh, int order) {
        return Category.builder()
                .code(code)
                .nameEn(en)
                .nameZh(zh)
                .sortOrder(order)
                .active(true)
                .build();
    }

    @Bean
    public ApplicationRunner seedCategories(CategoryRepository categories) {
        return args -> {
            int inserted = 0;
            for (Category c : SEED) {
                if (!categories.existsByCode(c.getCode())) {
                    categories.save(Category.builder()
                            .code(c.getCode())
                            .nameEn(c.getNameEn())
                            .nameZh(c.getNameZh())
                            .sortOrder(c.getSortOrder())
                            .active(true)
                            .build());
                    inserted++;
                }
            }
            if (inserted > 0) {
                log.info("category.seed inserted={} totalInTable={}", inserted, categories.count());
            }
        };
    }
}
