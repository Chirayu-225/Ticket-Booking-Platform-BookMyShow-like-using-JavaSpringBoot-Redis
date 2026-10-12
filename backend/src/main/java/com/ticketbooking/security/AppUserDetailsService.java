package com.ticketbooking.security;

import com.ticketbooking.domain.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        return userRepository.findByEmail(normalized)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }
}
