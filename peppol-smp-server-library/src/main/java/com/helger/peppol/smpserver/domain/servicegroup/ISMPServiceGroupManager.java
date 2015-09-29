package com.helger.peppol.smpserver.domain.servicegroup;

import java.util.Collection;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.state.EChange;
import com.helger.peppol.identifier.IParticipantIdentifier;

public interface ISMPServiceGroupManager
{
  @Nonnull
  ISMPServiceGroup createSMPServiceGroup (@Nonnull @Nonempty String sOwnerID,
                                          @Nonnull IParticipantIdentifier aParticipantIdentifier,
                                          @Nullable String sExtension);

  @Nonnull
  EChange updateSMPServiceGroup (@Nullable String sSMPServiceGroupID,
                                 @Nonnull String sOwnerID,
                                 @Nullable String sExtension);

  @Nonnull
  EChange deleteSMPServiceGroup (@Nullable IParticipantIdentifier aParticipantIdentifier);

  @Nonnull
  EChange deleteSMPServiceGroup (@Nullable ISMPServiceGroup aSMPServiceGroup);

  @Nonnull
  @ReturnsMutableCopy
  Collection <? extends ISMPServiceGroup> getAllSMPServiceGroups ();

  @Nonnull
  @ReturnsMutableCopy
  Collection <? extends ISMPServiceGroup> getAllSMPServiceGroupsOfOwner (@Nonnull String sOwnerID);

  @Nullable
  ISMPServiceGroup getSMPServiceGroupOfID (@Nullable IParticipantIdentifier aParticipantIdentifier);

  @Nullable
  ISMPServiceGroup getSMPServiceGroupOfID (@Nullable String sID);

  boolean containsSMPServiceGroupWithID (@Nullable String sID);

  @Nonnegative
  int getSMPServiceGroupCount ();
}