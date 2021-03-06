package ch.so.arp.nplvalidator.controllers;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.ServletContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class UploadController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    
    private static String FOLDER_PREFIX = "npl_";

    @Autowired
    private ServletContext servletContext;

    @Autowired
    CamelContext camelContext;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String upload() {
        return "upload";
    }
    
    @RequestMapping(value = "/", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> uploadFile(
            @RequestParam(name = "file", required = true) MultipartFile uploadFile, @RequestHeader String host) {
     
        try {
            // Get the file name.
            String fileName = uploadFile.getOriginalFilename();
            
            // If the upload button was pushed w/o choosing a file,
            // we just redirect to the starting page.
            if (uploadFile.getSize() == 0 || fileName.trim().equalsIgnoreCase("") || fileName == null) {
                log.warn("No file was uploaded. Redirecting to starting page.");
    
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", servletContext.getContextPath());
                return new ResponseEntity<String>(headers, HttpStatus.FOUND);
            }

            // Save the file in a temporary directory.
            Path tmpDirectory = Files.createTempDirectory(FOLDER_PREFIX);
            Path uploadFilePath = Paths.get(tmpDirectory.toString(), fileName);
    
            byte[] bytes = uploadFile.getBytes();
            Files.write(uploadFilePath, bytes);
            log.info(uploadFilePath.toString());
            
            // Send message to route.
            ProducerTemplate template = camelContext.createProducerTemplate();
            
            Exchange exchange = ExchangeBuilder.anExchange(camelContext)
                    .withBody(uploadFilePath.toFile())
                    .withHeader(Exchange.FILE_NAME, uploadFilePath.toFile().getName())
                    .withHeader("DBSCHEMA", tmpDirectory.toFile().getName())
                    .build();

            // Asynchronous request
            //template.asyncSend("direct:nplValidator", exchange);
            
            // Synchronous request
            Exchange result = template.send("direct:nplValidator", exchange);

            if (result.isFailed()) {
                return ResponseEntity.badRequest().contentType(MediaType.parseMediaType("text/plain")).body(result.getException().getMessage());
            } else {

                // TODO:
                // - Get bounding box from Geoserver rest api?
                // - How to deal with public host and port of Geoserver?
                String namespace = tmpDirectory.toFile().getName();
                String body = "<html><a href='http://localhost:8080/geoserver/"+namespace+"/wms?service=WMS&version=1.1.0&request=GetMap&layers="+namespace+"%3Anpl_grundnutzung&bbox=2637457.705%2C1250408.875%2C2640056.222%2C1252131.905&width=768&height=509&srs=EPSG%3A2056&format=application/openlayers'>http://localhost:8080/geoserver/"+namespace+"/wms?service=WMS&version=1.1.0&request=GetMap&layers="+namespace+"%3Anpl_grundnutzung&bbox=2637457.705%2C1250408.875%2C2640056.222%2C1252131.905&width=768&height=509&srs=EPSG%3A2056&format=application/openlayers</a></<html>";                
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return ResponseEntity.badRequest().contentType(MediaType.parseMediaType("text/plain")).body(e.getMessage());
        }
    }
}
