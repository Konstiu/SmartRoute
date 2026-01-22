FROM julia:1.11-bullseye

WORKDIR /app
COPY Project.toml /app/Project.toml
COPY src /app/src

RUN julia --project=. -e 'using Pkg; Pkg.instantiate(); Pkg.precompile()'

EXPOSE 8081
CMD ["julia", "--project=.", "src/server.jl"]
