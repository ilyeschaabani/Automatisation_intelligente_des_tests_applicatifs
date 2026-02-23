package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Enum.RepoProvider;
import org.antlr.v4.runtime.misc.NotNull;

public record RepoResolveRequest(

        @NotNull RepoProvider provider,
          String repositoryUrl
) {}
