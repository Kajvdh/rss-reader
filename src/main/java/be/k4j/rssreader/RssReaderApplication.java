package be.k4j.rssreader;

import be.k4j.rssreader.models.PushbulletNotification;
import be.k4j.rssreader.models.RssEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.feed.synd.SyndEntry;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.feed.inbound.FeedEntryMessageSource;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

@RestController
@SpringBootApplication
public class RssReaderApplication {
    private static final Logger LOG = LoggerFactory.getLogger(RssReaderApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(RssReaderApplication.class, args);
    }

    @Autowired
    private Environment env;

    @Autowired
    private RssConfig rssConfig;


    @Bean
    @InboundChannelAdapter( value = "feedChannel",
            poller = @Poller(maxMessagesPerPoll = "100", fixedDelay = "15000"))
    public FeedEntryMessageSource feedAdapter() throws MalformedURLException {
        Authenticator.setDefault(new Authenticator() {

            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(rssConfig.getLogin(), rssConfig.getPass().toCharArray());
            }
        });


        return new FeedEntryMessageSource(new URL(rssConfig.getLink()), "feedChannel");
    }


    @MessageEndpoint
    public static class Endpoint {

        private RestTemplate restTemplate = new RestTemplate();
        private PushbulletConfig pushbulletConfig = new PushbulletConfig();

        @ServiceActivator(inputChannel = "feedChannel", poller = @Poller(maxMessagesPerPoll = "100", fixedDelay = "15000"))
        public void log(Message<SyndEntry> message) throws Exception {
            SyndEntry payload = message.getPayload();
            String b = payload.getDescription().getValue();
            RssEntry rssEntry = new RssEntry(payload.getTitle(), Jsoup.parse(payload.getDescription().getValue()).text(), payload.getLink(), payload.getPublishedDate());

            //LOG.info(rssEntry.toString());
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR_OF_DAY, -3);


            if (rssEntry.getDate().after(cal.getTime())) {

                HttpHeaders headers = new HttpHeaders();
                headers.set("Access-Token", pushbulletConfig.getApiKey());
                headers.setContentType(MediaType.APPLICATION_JSON);


                PushbulletNotification notification = new PushbulletNotification();
                notification.setType("note");
                notification.setTitle(rssEntry.getTitle());
                notification.setBody("[["+ rssEntry.getDate().toString() +"]]" + rssEntry.getValue());

                ArrayList<String> saveMoons = new ArrayList<>();
                saveMoons.add("1:77:8");
                saveMoons.add("1:159:9");
                saveMoons.add("1:240:9");

                cal.setTime(new Date());
                cal.set(Calendar.HOUR_OF_DAY, 7);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                Date startSaveTime = cal.getTime();

                cal.setTime(new Date());
                cal.set(Calendar.HOUR_OF_DAY, 20);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                Date endSaveTime = cal.getTime();

                Boolean notificationIsAboutSaveMoons = (saveMoons.stream().map(b::contains).filter(x -> x).count() > 0);
                Boolean notificationIsAboutRip = (b.contains("Ster des Doods"));

                Boolean notificationIsAboutSpy = (b.contains("is ontdekt in de buurt van je planeet"));
                Boolean notificationIsAboutReturnToSaveMoon = (saveMoons.stream().map(y -> b.contains("naar planeet ["+y+"]")).filter(x -> x).count() > 0);
                Boolean notificationIsBetweenSaveTimes = (rssEntry.getDate().after(startSaveTime) && rssEntry.getDate().before(endSaveTime));

                Boolean notificationIsUrgent = (notificationIsAboutSaveMoons && (notificationIsAboutRip || notificationIsAboutSpy ));

                if (notificationIsUrgent) {
                    ObjectMapper mapper = new ObjectMapper();
                    String str = mapper.writeValueAsString(notification);

                    LOG.info("Going to send notification: " + str);
                    HttpEntity<String> entity = new HttpEntity<>(str, headers);
                    restTemplate.postForEntity(pushbulletConfig.getUrl(), entity, String.class);
                }
            }
        }
    }


    @Bean
    public MessageChannel feedChannel() {
        return new QueueChannel(500);
    }

//    @Bean(name = PollerMetadata.DEFAULT_POLLER)
//    public PollerMetadata poller() {
//        PeriodicTrigger trigger = new PeriodicTrigger(100000);
//        trigger.setFixedRate(true);
//        PollerMetadata pollerMetadata = new PollerMetadata();
//        pollerMetadata.setTrigger(trigger);
//        return pollerMetadata;
//    }
}
