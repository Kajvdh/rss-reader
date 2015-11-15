package be.k4j.rssreader;

import be.k4j.rssreader.models.PushbulletNotification;
import be.k4j.rssreader.models.RssEntry;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
import java.util.Calendar;
import java.util.Date;

@RestController
@SpringBootApplication
public class RssreaderApplication {
    private static final Logger LOG = LoggerFactory.getLogger(RssreaderApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(RssreaderApplication.class, args);
    }

    @Autowired
    private Environment env;

    @Autowired
    private RssConfig rssConfig;


    @Bean
    @InboundChannelAdapter( value = "feedChannel",
            poller = @Poller(maxMessagesPerPoll = "100", fixedRate = "5000"))
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

        @ServiceActivator(inputChannel = "feedChannel")
        public void log(Message<SyndEntry> message) throws Exception {
            SyndEntry payload = message.getPayload();
            RssEntry rssEntry = new RssEntry(payload.getTitle(), Jsoup.parse(payload.getDescription().getValue()).text(), payload.getLink(), payload.getPublishedDate());

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
                notification.setBody(rssEntry.getValue());

                ObjectMapper mapper = new ObjectMapper();
                String str = mapper.writeValueAsString(notification);



                HttpEntity<String> entity = new HttpEntity<>(str, headers);
                LOG.warn(entity.toString());
                restTemplate.postForEntity(pushbulletConfig.getUrl(), entity, String.class);
                LOG.info(rssEntry.toString());
            }
        }
    }


    @Bean
    public MessageChannel feedChannel() {
        return new QueueChannel(500);
    }

    @Bean(name = PollerMetadata.DEFAULT_POLLER)
    public PollerMetadata poller() {
        PeriodicTrigger trigger = new PeriodicTrigger(10);
        trigger.setFixedRate(true);
        PollerMetadata pollerMetadata = new PollerMetadata();
        pollerMetadata.setTrigger(trigger);
        return pollerMetadata;
    }
}
