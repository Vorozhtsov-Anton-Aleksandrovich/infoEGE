package com.github.anton.infoege.course;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Статика для задач курсов. Pyodide лежит в {@code static/pyodide/<версия>/} (его распаковывает Maven при сборке):
 * версия в пути, поэтому файлы кэшируются в браузере на год. После первой загрузки Python запускается из кэша.
 */
@Configuration
@EnableConfigurationProperties(CourseWebConfig.Versions.class)
public class CourseWebConfig implements WebMvcConfigurer {

    /** Версии из pom.xml (application.yml, app.courses.*): по ним строятся пути в шаблонах. */
    @ConfigurationProperties("app.courses")
    public record Versions(String pyodideVersion, String codemirrorVersion) {

        public String pyodideUrl() {
            return "/pyodide/" + pyodideVersion + "/";
        }

        public String codemirrorUrl() {
            return "/webjars/codemirror/" + codemirrorVersion + "/";
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl year = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
        registry.addResourceHandler("/pyodide/**")
                .addResourceLocations("classpath:/static/pyodide/")
                .setCacheControl(year);
    }
}
