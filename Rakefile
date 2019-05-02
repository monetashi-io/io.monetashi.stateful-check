task :clean do
  sh %[rm -fR target]
end

task :pom do
   puts %[clj -Spom]
end

# https://juxt.pro/blog/posts/pack-maven.html
task :build => [:clean, :pom] do
  sh %[clj -A:pack mach.pack.alpha.skinny --no-libs --project-path target/stateful-check.jar]
end

# export CLOJARS_USERNAME=...
# export CLOJARS_PASSWORD=..
task :deploy => :build do
  sh %[clj -A:deploy target/stateful-check.jar]
end
