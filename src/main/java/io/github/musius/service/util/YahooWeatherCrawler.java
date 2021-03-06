package io.github.musius.service.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.musius.domain.WeatherData;
import io.github.musius.domain.util.YahooDateTimeDeserializer;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.inject.Inject;
import java.io.IOException;

@Component
public class YahooWeatherCrawler implements WeatherCrawler {
    public static final String DATA_SOURCE_URI = "yahooapis.com";
    @Inject
    RestTemplate restTemplate;

    @Override
    public WeatherData getDataForCity(String cityName) {
        String json = restTemplate.getForObject(formatUri(cityName), String.class);
        ParseTemplate parsed = parse(json);
        return parsed.toWeatherData(cityName);
    }

    @Override
    public String getSupportedDataSourceUri() {
        return DATA_SOURCE_URI;
    }

    private ParseTemplate parse(String json) {
        ObjectMapper mapper = createObjectMapper();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode part = root.at("/query/results/channel/item/condition");
            return mapper.readValue(part.toString(), ParseTemplate.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ObjectMapper createObjectMapper() {
        Jackson2ObjectMapperFactoryBean bean = new Jackson2ObjectMapperFactoryBean();
        bean.afterPropertiesSet();
        return bean.getObject();
    }

    private String formatUri(String city) {
        return "https://query.yahooapis.com/v1/public/yql?q=" +
                "select item.condition from weather.forecast where woeid in " +
                "(select woeid from geo.places(1) where text='" + city + "') and u='c'&format=json";
    }

    /**
     * Шаблон-заглушка, необходимая для правильного парсинга json от yahoo с дальнейим преобразованием в WeatherData
     */
    private static class ParseTemplate {
        public Double temp;
        @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
        @JsonDeserialize(using = YahooDateTimeDeserializer.class)
        public DateTime date;

        WeatherData toWeatherData(String cityName) {
            WeatherData weatherData = new WeatherData();
            weatherData.setDate(date);
            weatherData.setTemperatureCelsius(temp);
            weatherData.setCityName(cityName);
            weatherData.setDataSourceUri(DATA_SOURCE_URI);
            return weatherData;
        }
    }
}
