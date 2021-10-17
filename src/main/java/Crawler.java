import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

public class Crawler {

    JsonArray articlesArray = new JsonArray();
    JsonArray categoriesArray = new JsonArray();
    JsonArray tagsArray = new JsonArray();
    JsonArray threadsArray = new JsonArray();

    Random random = new Random();

    public static String deAccent(String str) {
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String normalized = pattern.matcher(nfdNormalizedString).replaceAll("");
        return normalized.replace(" ", "-").replace("/", "-").toLowerCase(Locale.ROOT);
    }

    void start(String url, int count, String categoryId, String categoryName, String tagId, String tagName) {
        int totalPages = count;
//        int totalPages = 1;
        for (int i = 1; i <= totalPages; i++) {
            String baseUrl = "%s?pg=%d";
            visit(String.format(baseUrl, url, i), categoryId, categoryName, tagId, tagName);
        }
    }

    void visit(String page, String categoryId, String categoryName, String tagId, String tagName) {
        System.out.println(String.format("    - %s", page));
        try {
            Document doc = Jsoup.connect(page).get();
            Elements newsHeadlines = doc.select("div.one-recipes");
            for (Element element : newsHeadlines) {
                JsonObject articleObject = new JsonObject();
                String name = element.select("h3").text();
                String url = element.select("div.info-list a").attr("href");
                Element thumbnailElement = element.select("img").last();
                String thumbnail = thumbnailElement.attr("src");
                int width = Integer.parseInt(thumbnailElement.attr("width"));
                int height = Integer.parseInt(thumbnailElement.attr("height"));
                Document pageDoc = Jsoup.connect(url).get();
                Elements items = pageDoc.select("div.row.detail_note li");
                String level = items.get(0).text();
                String people = items.get(1).text().split(" ")[0];
                StringBuilder descriptionBuilder = new StringBuilder();
                for (Element item : pageDoc.select("div.row.mb-3")) {
                    String title = item.select("h4").text();
                    String content = item.select("div.col div").text();
                    descriptionBuilder.append("\n");
                    descriptionBuilder.append(title).append("\n");
                    descriptionBuilder.append(content).append("\n");
                }
                String description = descriptionBuilder.toString().trim();
//                System.out.println("");
//                System.out.println("  - name       : " + name);
//                System.out.println("  - url        : " + url);
//                System.out.println("  - thumbnail  : " + thumbnail);
//                System.out.println("  - level      : " + level);
//                System.out.println("  - people     : " + people);
//                System.out.println("  - description: " + description);
                articleObject.addProperty("name", name);
                articleObject.addProperty("url", url);
//                object.addProperty("people", people);
//                object.addProperty("level", level);
                articleObject.addProperty("description", description);

                JsonObject thumbnailObject = new JsonObject();
                thumbnailObject.addProperty("url", thumbnail);
                thumbnailObject.addProperty("width", width);
                thumbnailObject.addProperty("height", height);
                articleObject.add("thumbnail", thumbnailObject);

                JsonObject category = new JsonObject();
                category.addProperty("slug", categoryId);
                category.addProperty("name", categoryName);
                JsonArray categories = new JsonArray();
                categories.add(category);
                articleObject.add("categories", categories);

                JsonObject tag = new JsonObject();
                tag.addProperty("slug", tagId);
                tag.addProperty("name", tagName);
                JsonArray tags = new JsonArray();
                tags.add(tag);
                articleObject.add("tags", tags);

                articleObject.addProperty("views", 10 + random.nextInt((int) Math.pow(10, random.nextInt(6))));
                articleObject.addProperty("likes", 10 + random.nextInt((int) Math.pow(10, random.nextInt(6))));
                articleObject.addProperty("shares", 10 + random.nextInt((int) Math.pow(10, random.nextInt(6))));
                articleObject.addProperty("comments", 10 + random.nextInt((int) Math.pow(10, random.nextInt(6))));

                articlesArray.add(articleObject);
                System.out.println(String.format("      . %04d | %s", articlesArray.size(), articleObject));
            }
        } catch (Exception ex) {
            System.out.println("    > " + ex.getLocalizedMessage());
        }
    }

    void getCategory() {
        String url = "https://monngonmoingay.com";
        System.out.println(url);
        try {
            Document doc = Jsoup.connect(url).get();
            Elements categories = doc.select("div.row.onlydesktop div.row-taxonomy");
            for (Element categoryElement : categories) {
                String categoryName = categoryElement.select("h3").text();
                String categorySlug = deAccent(categoryName);

                JsonObject category = new JsonObject();
                category.addProperty("slug", categorySlug);
                category.addProperty("name", categoryName);
                categoriesArray.add(category);

                System.out.println("* " + categoryName + " | " + categorySlug);
                Elements items = categoryElement.select("a");

                JsonObject thread = new JsonObject();
                thread.addProperty("name", categoryName);
                JsonArray params = new JsonArray();

                for (Element item : items) {
                    String tagUrl = item.attr("href");
                    String tagName = item.text();

                    String[] tagSlugs = tagUrl.split("/");
                    String tagSlug = tagSlugs[tagSlugs.length - 1].replace("mon-ngon-tu-", "");
                    int count = pageCount(tagUrl);

                    JsonObject tag = new JsonObject();
                    tag.addProperty("slug", tagSlug);
                    tag.addProperty("name", tagName);
                    tagsArray.add(tag);

                    JsonObject param = new JsonObject();
                    param.addProperty("key", "tag");
                    param.addProperty("value", tagSlug);
                    params.add(param);

                    System.out.println("  + " + tagName + "| " + tagSlug + " | " + count + " | " + tagUrl);
                    start(tagUrl, count, categorySlug, categoryName, tagSlug, tagName);
                }
                thread.add("params", params);
                threadsArray.add(thread);
            }
        } catch (Exception ex) {
            System.out.println("visit " + ex.getLocalizedMessage());
        }
        writeOutput();
    }

    int pageCount(String url) {
        int count = 1;
        try {
            Document document = Jsoup.connect(url).get();
            Elements elements = document.select("div.paging a");
            String countText = elements.get(elements.size() - 2).text();
            count = Integer.parseInt(countText.trim());
            return count;
        } catch (Exception ex) {
            return count;
        }
    }

    void writeOutput() {
        String json = articlesArray.toString();
        String categoriesJson = categoriesArray.toString();
        String tagsJson = tagsArray.toString();
        String threadsJson = threadsArray.toString();
        System.out.println(json);
        File dataFile = new File("data");
        if (dataFile.exists()) {
            dataFile.delete();
        }
        dataFile.mkdir();
        try {
            File file = new File(dataFile, "articles.json");
            IOUtils.write(json, new FileOutputStream(file), Charsets.UTF_8);

            File categoriesFile = new File(dataFile, "categories.json");
            IOUtils.write(categoriesJson, new FileOutputStream(categoriesFile), Charsets.UTF_8);

            File tagsFile = new File(dataFile, "tags.json");
            IOUtils.write(tagsJson, new FileOutputStream(tagsFile), Charsets.UTF_8);

            File threadsFile = new File(dataFile, "threads.json");
            IOUtils.write(threadsJson, new FileOutputStream(threadsFile), Charsets.UTF_8);
        } catch (Exception ex) {
            System.out.println("start " + ex.getLocalizedMessage());
        }
    }
}
