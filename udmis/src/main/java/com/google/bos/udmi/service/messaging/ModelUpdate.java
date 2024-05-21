package com.google.bos.udmi.service.messaging;

import udmi.schema.Metadata;

/**
 * Empty wrapper class to have a type name of ModelUpdate that just simply implements the
 * Metadata type that otherwise would have no subFolder associated with it.
 *
 * This is because elsewhere, a subfolder of `model_update` is registered with any object of this
 * class
 */
public class ModelUpdate extends Metadata {

}