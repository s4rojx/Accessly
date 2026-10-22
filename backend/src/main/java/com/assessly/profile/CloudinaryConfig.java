package com.assessly.profile;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;

@Configuration
public class CloudinaryConfig {
    @Bean
    @Conditional(CloudinaryConfiguredCondition.class)
    Cloudinary cloudinary(@Value("${CLOUDINARY_URL:}") String cloudinaryUrl) {
        Cloudinary cloudinary = new Cloudinary(cloudinaryUrl);
        cloudinary.config.secure = true;
        return cloudinary;
    }
}
