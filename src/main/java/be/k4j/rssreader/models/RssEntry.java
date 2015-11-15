package be.k4j.rssreader.models;


import java.util.Date;

public class RssEntry {
    private String title;
    private String value;
    private String link;
    private Date date;

    @Override
    public String toString() {
        return "RssEntry{" +
                "title='" + title + '\'' +
                ", value='" + value + '\'' +
                ", link='" + link + '\'' +
                ", date=" + date +
                '}';
    }

    public RssEntry(String title, String value, String link, Date date) {
        this.title = title;
        this.value = value;
        this.link = link;
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
