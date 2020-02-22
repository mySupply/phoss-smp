/**
 * Copyright (C) 2015-2020 Philip Helger and contributors
 * philip[at]helger[dot]com
 *
 * The Original Code is Copyright The Peppol project (http://www.peppol.eu)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.helger.phoss.smp.backend.sql.mgr;

import java.security.cert.X509Certificate;
import java.util.Optional;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.callback.CallbackList;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.mutable.MutableBoolean;
import com.helger.commons.state.EChange;
import com.helger.commons.state.ESuccess;
import com.helger.db.jdbc.callback.ConstantPreparedStatementDataProvider;
import com.helger.db.jdbc.executor.DBResultRow;
import com.helger.peppolid.IDocumentTypeIdentifier;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.peppolid.simple.doctype.SimpleDocumentTypeIdentifier;
import com.helger.peppolid.simple.participant.SimpleParticipantIdentifier;
import com.helger.phoss.smp.backend.sql.AbstractJDBCEnabledManager;
import com.helger.phoss.smp.domain.redirect.ISMPRedirect;
import com.helger.phoss.smp.domain.redirect.ISMPRedirectCallback;
import com.helger.phoss.smp.domain.redirect.ISMPRedirectManager;
import com.helger.phoss.smp.domain.redirect.SMPRedirect;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroup;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroupManager;
import com.helger.security.certificate.CertificateHelper;

/**
 * Manager for all {@link SMPRedirect} objects.
 *
 * @author Philip Helger
 */
public final class SMPRedirectManagerJDBC extends AbstractJDBCEnabledManager implements ISMPRedirectManager
{
  private final ISMPServiceGroupManager m_aServiceGroupMgr;
  private final CallbackList <ISMPRedirectCallback> m_aCallbacks = new CallbackList <> ();

  public SMPRedirectManagerJDBC (@Nonnull final ISMPServiceGroupManager aServiceGroupMgr)
  {
    m_aServiceGroupMgr = aServiceGroupMgr;
  }

  @Nonnull
  @ReturnsMutableObject
  public CallbackList <ISMPRedirectCallback> redirectCallbacks ()
  {
    return m_aCallbacks;
  }

  @Nullable
  public ISMPRedirect createOrUpdateSMPRedirect (@Nonnull final ISMPServiceGroup aServiceGroup,
                                                 @Nonnull final IDocumentTypeIdentifier aDocTypeID,
                                                 @Nonnull @Nonempty final String sRedirectUrl,
                                                 @Nonnull @Nonempty final String sSubjectUniqueIdentifier,
                                                 @Nullable final X509Certificate aCertificate,
                                                 @Nullable final String sExtension)
  {
    ValueEnforcer.notNull (aServiceGroup, "ServiceGroup");
    ValueEnforcer.notNull (aDocTypeID, "DocumentTypeIdentifier");

    final MutableBoolean aCreatedNew = new MutableBoolean (true);

    final ESuccess eSuccess = executor ().performInTransaction ( () -> {
      final ISMPRedirect aDBRedirect = getSMPRedirectOfServiceGroupAndDocumentType (aServiceGroup, aDocTypeID);

      final IParticipantIdentifier aParticpantID = aServiceGroup.getParticpantIdentifier ();
      final String sCertificate = aCertificate == null ? null
                                                       : CertificateHelper.getPEMEncodedCertificate (aCertificate);

      if (aDBRedirect == null)
      {
        // Create new
        final int nCreated = executor ().insertOrUpdateOrDelete ("INSERT INTO smp_service_metadata_redirection (businessIdentifierScheme, businessIdentifier, documentIdentifierScheme, documentIdentifier, redirectionUrl, certificateUID, certificate, extension) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                                                 new ConstantPreparedStatementDataProvider (aParticpantID.getScheme (),
                                                                                                            aParticpantID.getValue (),
                                                                                                            aDocTypeID.getScheme (),
                                                                                                            aDocTypeID.getValue (),
                                                                                                            sRedirectUrl,
                                                                                                            sSubjectUniqueIdentifier,
                                                                                                            sCertificate,
                                                                                                            sExtension));
        if (nCreated != 1)
          throw new IllegalStateException ("Failed to create new DB entry");
        aCreatedNew.set (true);
      }
      else
      {
        // Update existing
        final int nCreated = executor ().insertOrUpdateOrDelete ("UPDATE smp_service_metadata_redirection" +
                                                                 " SET redirectionUrl, certificateUID, certificate, extension" +
                                                                 " WHERE businessIdentifierScheme=? AND businessIdentifier=? AND documentIdentifierScheme=? AND documentIdentifier=?",
                                                                 new ConstantPreparedStatementDataProvider (sRedirectUrl,
                                                                                                            sSubjectUniqueIdentifier,
                                                                                                            sCertificate,
                                                                                                            sExtension,
                                                                                                            aParticpantID.getScheme (),
                                                                                                            aParticpantID.getValue (),
                                                                                                            aDocTypeID.getScheme (),
                                                                                                            aDocTypeID.getValue ()));
        if (nCreated != 1)
          throw new IllegalStateException ("Failed to update existing DB entry");
        aCreatedNew.set (false);
      }
    });

    if (eSuccess.isFailure ())
    {
      return null;
    }

    final SMPRedirect aRedirect = new SMPRedirect (aServiceGroup,
                                                   aDocTypeID,
                                                   sRedirectUrl,
                                                   sSubjectUniqueIdentifier,
                                                   aCertificate,
                                                   sExtension);

    if (aCreatedNew.booleanValue ())
      m_aCallbacks.forEach (x -> x.onSMPRedirectCreated (aRedirect));
    else
      m_aCallbacks.forEach (x -> x.onSMPRedirectUpdated (aRedirect));

    return aRedirect;
  }

  @Nonnull
  public EChange deleteSMPRedirect (@Nullable final ISMPRedirect aSMPRedirect)
  {
    if (aSMPRedirect == null)
      return EChange.UNCHANGED;

    final IParticipantIdentifier aParticipantID = aSMPRedirect.getServiceGroup ().getParticpantIdentifier ();
    final IDocumentTypeIdentifier aDocTypeID = aSMPRedirect.getDocumentTypeIdentifier ();
    final int nDeleted = executor ().insertOrUpdateOrDelete ("DELETE FROM smp_service_metadata_redirection" +
                                                             " WHERE businessIdentifierScheme=? AND businessIdentifier=? AND documentIdentifierScheme=? and documentIdentifier=?",
                                                             new ConstantPreparedStatementDataProvider (aParticipantID.getScheme (),
                                                                                                        aParticipantID.getValue (),
                                                                                                        aDocTypeID.getScheme (),
                                                                                                        aDocTypeID.getValue ()));
    if (nDeleted == 0)
      return EChange.UNCHANGED;

    m_aCallbacks.forEach (x -> x.onSMPRedirectDeleted (aSMPRedirect));
    return EChange.CHANGED;
  }

  @Nonnull
  public EChange deleteAllSMPRedirectsOfServiceGroup (@Nullable final ISMPServiceGroup aServiceGroup)
  {
    if (aServiceGroup == null)
      return EChange.UNCHANGED;

    // Remember all existing
    final ICommonsList <ISMPRedirect> aDeletedRedirects = getAllSMPRedirectsOfServiceGroup (aServiceGroup);

    // Now delete
    final IParticipantIdentifier aParticipantID = aServiceGroup.getParticpantIdentifier ();
    final int nDeleted = executor ().insertOrUpdateOrDelete ("DELETE FROM smp_service_metadata_redirection" +
                                                             " WHERE businessIdentifierScheme=? AND businessIdentifier=?",
                                                             new ConstantPreparedStatementDataProvider (aParticipantID.getScheme (),
                                                                                                        aParticipantID.getValue ()));
    if (nDeleted == 0)
      return EChange.UNCHANGED;

    // Callback only, if all were deleted
    if (nDeleted == aDeletedRedirects.size ())
    {
      for (final ISMPRedirect aRedirect : aDeletedRedirects)
        m_aCallbacks.forEach (x -> x.onSMPRedirectDeleted (aRedirect));
    }
    else
    {
      LOGGER.warn (nDeleted +
                   " SMP redirects were deleted, but " +
                   aDeletedRedirects.size () +
                   " were found previously. Because of this inconsistency, no callbacks are triggered");
    }
    return EChange.CHANGED;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPRedirect> getAllSMPRedirects ()
  {
    final Optional <ICommonsList <DBResultRow>> aDBResult = executor ().queryAll ("SELECT businessIdentifierScheme, businessIdentifier, documentIdentifierScheme, documentIdentifier, redirectionUrl, certificateUID, certificate, extension" +
                                                                                  " FROM smp_service_metadata_redirection");
    final ICommonsList <ISMPRedirect> ret = new CommonsArrayList <> ();
    if (aDBResult.isPresent ())
      for (final DBResultRow aRow : aDBResult.get ())
      {
        final ISMPServiceGroup aServiceGroup = m_aServiceGroupMgr.getSMPServiceGroupOfID (new SimpleParticipantIdentifier (aRow.getAsString (0),
                                                                                                                           aRow.getAsString (1)));
        final X509Certificate aCertificate = CertificateHelper.convertStringToCertficateOrNull (aRow.getAsString (6));
        ret.add (new SMPRedirect (aServiceGroup,
                                  new SimpleDocumentTypeIdentifier (aRow.getAsString (2), aRow.getAsString (3)),
                                  aRow.getAsString (4),
                                  aRow.getAsString (5),
                                  aCertificate,
                                  aRow.getAsString (7)));
      }
    return ret;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPRedirect> getAllSMPRedirectsOfServiceGroup (@Nullable final ISMPServiceGroup aServiceGroup)
  {
    final ICommonsList <ISMPRedirect> ret = new CommonsArrayList <> ();
    if (aServiceGroup != null)
    {
      final IParticipantIdentifier aParticipantID = aServiceGroup.getParticpantIdentifier ();
      final Optional <ICommonsList <DBResultRow>> aDBResult = executor ().queryAll ("SELECT documentIdentifierScheme, documentIdentifier, redirectionUrl, certificateUID, certificate, extension" +
                                                                                    " FROM smp_service_metadata_redirection" +
                                                                                    " WHERE businessIdentifierScheme=? AND businessIdentifier=?",
                                                                                    new ConstantPreparedStatementDataProvider (aParticipantID.getScheme (),
                                                                                                                               aParticipantID.getValue ()));
      if (aDBResult.isPresent ())
        for (final DBResultRow aRow : aDBResult.get ())
        {
          final X509Certificate aCertificate = CertificateHelper.convertStringToCertficateOrNull (aRow.getAsString (4));
          ret.add (new SMPRedirect (aServiceGroup,
                                    new SimpleDocumentTypeIdentifier (aRow.getAsString (0), aRow.getAsString (1)),
                                    aRow.getAsString (2),
                                    aRow.getAsString (3),
                                    aCertificate,
                                    aRow.getAsString (5)));
        }
    }
    return ret;
  }

  @Nonnegative
  public long getSMPRedirectCount ()
  {
    return executor ().queryCount ("SELECT COUNT(*) FROM smp_service_metadata_redirection");
  }

  @Nullable
  public ISMPRedirect getSMPRedirectOfServiceGroupAndDocumentType (@Nullable final ISMPServiceGroup aServiceGroup,
                                                                   @Nullable final IDocumentTypeIdentifier aDocTypeID)
  {
    if (aServiceGroup == null)
      return null;
    if (aDocTypeID == null)
      return null;

    final IParticipantIdentifier aParticipantID = aServiceGroup.getParticpantIdentifier ();
    final Optional <DBResultRow> aDBResult = executor ().querySingle ("SELECT redirectionUrl, certificateUID, certificate, extension" +
                                                                      " FROM smp_service_metadata_redirection" +
                                                                      " WHERE businessIdentifierScheme=? AND businessIdentifier=? AND documentIdentifierScheme=? and documentIdentifier=?",
                                                                      new ConstantPreparedStatementDataProvider (aParticipantID.getScheme (),
                                                                                                                 aParticipantID.getValue (),
                                                                                                                 aDocTypeID.getScheme (),
                                                                                                                 aDocTypeID.getValue ()));
    if (!aDBResult.isPresent ())
      return null;

    final DBResultRow aRow = aDBResult.get ();
    final X509Certificate aCertificate = CertificateHelper.convertStringToCertficateOrNull (aRow.getAsString (2));
    return new SMPRedirect (aServiceGroup,
                            aDocTypeID,
                            aRow.getAsString (0),
                            aRow.getAsString (1),
                            aCertificate,
                            aRow.getAsString (3));
  }
}