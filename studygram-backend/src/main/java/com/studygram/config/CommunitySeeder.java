package com.studygram.config;

import com.studygram.entity.Community;
import com.studygram.repository.CommunityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * CommunitySeeder - Fills the communities table on startup
 *
 * WHY THIS EXISTS
 *
 * StudyGram has a fixed set of topics. Every post is tagged with one or more,
 * every user picks some as interests, and each one has a community page.
 *
 * That list has to live in exactly one place. It used to live in the React
 * app as a hardcoded array, which meant the backend had no idea what a valid
 * topic was and the communities table sat permanently empty - so
 * GET /api/communities returned [] and the whole feature was dead.
 *
 * Now the list lives here, the frontend fetches it, and there is one source of
 * truth.
 *
 * CommandLineRunner is a Spring interface with one method, run(), which Spring
 * calls once after the application has finished starting.
 *
 * The seeder is IDEMPOTENT: it only inserts topics that are missing. Running it
 * on every startup is therefore safe, and adding a new topic to the list below
 * is all it takes to introduce one.
 */
@Component
public class CommunitySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CommunitySeeder.class);

    @Autowired
    private CommunityRepository communityRepository;

    /*
     * The canonical topic list, grouped by category.
     *
     * LinkedHashMap rather than HashMap because it preserves insertion order,
     * so the categories always appear in the UI in the order written here
     * instead of an arbitrary one.
     */
    private static final Map<String, List<String>> TOPICS_BY_CATEGORY = new LinkedHashMap<>();

    static {
        TOPICS_BY_CATEGORY.put("Technology", List.of(
                "Programming", "Web Development", "Mobile Development", "Data Science",
                "Machine Learning", "Artificial Intelligence", "Cybersecurity",
                "Cloud Computing", "DevOps", "Blockchain", "Game Development",
                /*
                 * "UX Design", not "UI/UX Design".
                 *
                 * A community's name is its lowercased display name, and that name
                 * goes into a URL: /api/communities/{name}/posts. A slash inside it
                 * is not data, it is another path segment - so this one topic, alone
                 * out of 65, was unreachable from Browse topics.
                 *
                 * Escaping does not save it either: Spring rejects encoded slashes
                 * in paths by default, and that default is a path-traversal guard
                 * worth keeping. Renaming one topic is the cheaper trade.
                 */
                "UX Design"
        ));
        TOPICS_BY_CATEGORY.put("Sciences", List.of(
                "Mathematics", "Physics", "Chemistry", "Biology", "Statistics",
                "Astronomy", "Environmental Science", "Neuroscience"
        ));
        TOPICS_BY_CATEGORY.put("Business & Finance", List.of(
                "Business", "Entrepreneurship", "Marketing", "Finance", "Accounting",
                "Economics", "Stock Market", "Cryptocurrency", "Management"
        ));
        TOPICS_BY_CATEGORY.put("Languages & Communication", List.of(
                "English", "Spanish", "French", "German", "Chinese", "Japanese",
                "Arabic", "Public Speaking", "Writing", "Communication Skills"
        ));
        TOPICS_BY_CATEGORY.put("Creative & Arts", List.of(
                "Graphic Design", "Photography", "Video Editing", "Music", "Drawing",
                "Animation", "3D Modeling", "Content Creation"
        ));
        TOPICS_BY_CATEGORY.put("Health & Lifestyle", List.of(
                "Medicine", "Psychology", "Nutrition", "Fitness", "Mental Health"
        ));
        TOPICS_BY_CATEGORY.put("Academic & Professional", List.of(
                "Law", "Engineering", "Architecture", "Education", "Research",
                "Project Management", "Leadership", "Career Development"
        ));
        TOPICS_BY_CATEGORY.put("Humanities", List.of(
                "History", "Philosophy", "Sociology", "Political Science", "Geography"
        ));
    }

    @Override
    public void run(String... args) {

        int created = 0;
        int repaired = 0;

        for (Map.Entry<String, List<String>> entry : TOPICS_BY_CATEGORY.entrySet()) {

            String category = entry.getKey();

            for (String displayName : entry.getValue()) {

                // Communities are keyed by lowercase name so that "Web Development"
                // and "web development" can never become two different communities.
                String name = displayName.toLowerCase();

                String description = "Share what you're learning about " + displayName
                        + ", ask questions, and find study buddies.";

                Community existing = communityRepository.findByName(name).orElse(null);

                if (existing == null) {
                    Community community = new Community();
                    community.setName(name);
                    community.setDisplayName(displayName);
                    community.setCategory(category);
                    community.setDescription(description);

                    communityRepository.save(community);
                    created++;
                    continue;
                }

                /*
                 * REPAIR RATHER THAN SKIP
                 *
                 * A first version of this seeder only inserted rows that were
                 * missing. That left any community created before this class
                 * existed - or before the `category` column was added - sitting
                 * in the table with half its fields empty, showing up in the UI
                 * under a fallback "Other" heading forever.
                 *
                 * Filling in blanks on rows that already exist makes the seeder
                 * self-healing: it converges the table on the list above no
                 * matter what state it started in. Only empty fields are
                 * touched, so nothing deliberately customised gets overwritten.
                 */
                boolean changed = false;

                if (existing.getCategory() == null || existing.getCategory().isBlank()) {
                    existing.setCategory(category);
                    changed = true;
                }

                if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) {
                    existing.setDisplayName(displayName);
                    changed = true;
                }

                if (existing.getDescription() == null || existing.getDescription().isBlank()) {
                    existing.setDescription(description);
                    changed = true;
                }

                if (changed) {
                    communityRepository.save(existing);
                    repaired++;
                }
            }
        }

        log.info("CommunitySeeder: {} created, {} repaired.", created, repaired);
    }

}
