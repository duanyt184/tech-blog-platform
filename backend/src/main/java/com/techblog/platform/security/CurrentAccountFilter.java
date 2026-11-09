package com.techblog.platform.security;
import com.techblog.platform.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
public class CurrentAccountFilter extends OncePerRequestFilter {
 private final UserRepository users;
 public CurrentAccountFilter(UserRepository users){this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null && auth.getPrincipal() instanceof UserPrincipal old){
   var account=users.findById(old.getId());
   if(account.isEmpty() || !account.get().isEnabled()){
    SecurityContextHolder.clearContext();
    if(request.getSession(false)!=null) request.getSession(false).invalidate();
   } else {
    var p=UserPrincipal.from(account.get());
    var context=SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    SecurityContextHolder.setContext(context);
   }
  }
  chain.doFilter(request,response);
 }
}
