package mobile;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import javax.servlet.MultipartConfigElement;

/**
 * @author
 */
@Configuration
public class TomcatConfig {

    @Value("${spring.server.port}")
    private String port;
    @Value("${spring.server.acceptorThreadCount}")
    private String acceptorThreadCount;
    @Value("${spring.server.minSpareThreads}")
    private String minSpareThreads;
    @Value("${spring.server.maxSpareThreads}")
    private String maxSpareThreads;
    @Value("${spring.server.maxThreads}")
    private String maxThreads;
    @Value("${spring.server.maxConnections}")
    private String maxConnections;
    @Value("${spring.server.protocol}")
    private String protocol;
    @Value("${spring.server.redirectPort}")
    private String redirectPort;
    @Value("${spring.server.compression}")
    private String compression;
    @Value("${spring.server.connectionTimeout}")
    private String connectionTimeout;

    @Value("${spring.server.MaxFileSize}")
    private DataSize MaxFileSize;
    @Value("${spring.server.MaxRequestSize}")
    private DataSize MaxRequestSize;

    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        tomcat.addConnectorCustomizers(new GwsTomcatConnectionCustomizer());
        return tomcat;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(MaxFileSize); // KB,MB
        factory.setMaxRequestSize(MaxRequestSize);
        return factory.createMultipartConfig();
    }

    public class GwsTomcatConnectionCustomizer implements TomcatConnectorCustomizer {

        public GwsTomcatConnectionCustomizer() {
        }

        @Override
        public void customize(Connector connector) {
            connector.setPort(Integer.valueOf(port));
            connector.setAttribute("connectionTimeout", connectionTimeout);
            connector.setAttribute("acceptorThreadCount", acceptorThreadCount);
            connector.setAttribute("minSpareThreads", minSpareThreads);
            connector.setAttribute("maxSpareThreads", maxSpareThreads);
            connector.setAttribute("maxThreads", maxThreads);
            connector.setAttribute("maxConnections", maxConnections);
            connector.setAttribute("protocol", protocol);
            connector.setAttribute("redirectPort", "redirectPort");
            connector.setAttribute("compression", "compression");
        }
    }
}
