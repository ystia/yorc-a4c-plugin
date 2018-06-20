#!/usr/bin/env python
#
# See https://cloud.google.com/pubsub/docs/reference/libraries
# See https://cloud.google.com/vision/docs/libraries
# https://google-cloud-python.readthedocs.io/en/latest/vision/index.html
import argparse
import json
import time
import os
import tempfile

from google.cloud import pubsub_v1
from google.cloud import storage
from google.cloud import vision
from google.cloud.vision import types
from enum import Enum
from io import BytesIO
from PIL import Image, ImageDraw


vision_client = vision.ImageAnnotatorClient()
storage_client = storage.Client()

# Document text detection types
class FeatureType(Enum):
    PAGE = 1
    BLOCK = 2
    PARA = 3
    WORD = 4
    SYMBOL = 5

# Names of likelihood from google.cloud.vision.enums
likelihood_name = ('UNKNOWN', 'VERY_UNLIKELY', 'UNLIKELY', 'POSSIBLE',
    'LIKELY', 'VERY_LIKELY')

def draw_boxes(image, bounds, color):

    draw = ImageDraw.Draw(image)

    i = 1
    for bound in bounds:
        draw.polygon([
            bound.vertices[0].x, bound.vertices[0].y,
            bound.vertices[1].x, bound.vertices[1].y,
            bound.vertices[2].x, bound.vertices[2].y,
            bound.vertices[3].x, bound.vertices[3].y], None, color)
        draw.text((bound.vertices[0].x, bound.vertices[0].y - 16),'{}'.format(i),(0,0,0))
        i += 1
    return image

def get_text_annotations_bounds(annotations, feature):

    bounds = []
    # Collect specified feature bounds by enumerating all document features
    for page in annotations.pages:
        for block in page.blocks:
            for paragraph in block.paragraphs:
                for word in paragraph.words:
                    for symbol in word.symbols:
                        if (feature == FeatureType.SYMBOL):
                            bounds.append(symbol.bounding_box)

                    if (feature == FeatureType.WORD):
                        bounds.append(word.bounding_box)

                if (feature == FeatureType.PARA):
                    bounds.append(paragraph.bounding_box)

            if (feature == FeatureType.BLOCK):
                bounds.append(block.bounding_box)

        if (feature == FeatureType.PAGE):
            bounds.append(block.bounding_box)

    return bounds

def add_likelihood(likelihood, expected, value, result):
    if likelihood_name[likelihood] == expected:
        result.append(value)

def get_likelihoods(face, likelihood):
    resultList = []
    add_likelihood(face.joy_likelihood, likelihood, "Joy", resultList)
    add_likelihood(face.sorrow_likelihood, likelihood, "Sorrow", resultList)
    add_likelihood(face.anger_likelihood, likelihood, "Anger", resultList)
    add_likelihood(face.surprise_likelihood, likelihood, "Surprise", resultList)
    add_likelihood(face.under_exposed_likelihood, likelihood, "Underexposed", resultList)
    add_likelihood(face.blurred_likelihood, likelihood, "Blurred", resultList)
    add_likelihood(face.headwear_likelihood, likelihood, "Head wear", resultList)

    result = ""
    if len(resultList) > 0:
        result = ', '.join(resultList)
    
    return result
    

def get_faces_likelihoods(faces):
    html_result = ""
    i = 1
    if len(faces) > 0:
        html_result = """
        <table>
        <tr><th>Face ID</th><th>Very likely</th><th>Likely</th></tr>
        """
        for face in faces:
            html_result += '<tr><td>{}</td><td>{}</td><td>{}</td></tr>\n'.format(
                i, get_likelihoods(face, "VERY_LIKELY"), get_likelihoods(face, "LIKELY"))
            i += 1

        html_result += '</table><br/>\n'
    return html_result

def render_face_annotations(bucket_name, blob_name, faces, out_bucket_name):
    bucket = storage_client.get_bucket(bucket_name)
    blob = bucket.blob(blob_name)
    byte_stream = BytesIO()
    blob.download_to_file(byte_stream)
    byte_stream.seek(0)
    image = Image.open(byte_stream)
    draw = ImageDraw.Draw(image)
    
    i = 1
    for face in faces:
        box = [(vertex.x, vertex.y)
               for vertex in face.bounding_poly.vertices]
        draw.line(box + [box[0]], width=5, fill='#00ff00')
        draw.text((box[0][0], box[0][1] - 16),'{}'.format(i),(0,0,0))
        i += 1

    html_result = "\n<p>No face detected.</p>\n"
    if i > 1:
        out_bucket = storage_client.get_bucket(out_bucket_name)
        file_name = "faces.jpg"
        out_blob_name = '{}/{}'.format(os.path.splitext(blob_name)[0], file_name)
        tmp_fd, tmp_file_name = tempfile.mkstemp('.jpg')
        os.close(tmp_fd)
        image.save(tmp_file_name)
        out_blob = out_bucket.blob(out_blob_name)
        out_blob.upload_from_filename(tmp_file_name)
        out_blob.make_public()
        os.remove(tmp_file_name)
        html_result = '<img src={} alt="Faces">'.format(file_name)
            
    return html_result

def get_web_annotations(bucket_name, blob_name, annotations):

    html_result = '\n<p>No web page matching found.</p>\n'

    if annotations.pages_with_matching_images:
        if len(annotations.pages_with_matching_images) > 0:
            html_result = """
            <table>
            <tr><th style="text-align: left;">URLs found</th></tr>
            """                
            
            for page in annotations.pages_with_matching_images:
                html_result += '<tr><td style="text-align: left;"><a href="{}">{}</a></td></tr>\n'.format(page.url, page.url)

            html_result += '</table>\n'
    
    return html_result

def get_label_annotations(bucket_name, blob_name, annotations):

    html_result = '\n<p>No label found.</p>\n'
    if len(annotations) > 0:
        html_result = """
        <table>
        <tr><th>Label</th><th>Score</th></tr>
        """
        for label in annotations:
            html_result += '<tr><td>{}</td><td>{}</td></tr>\n'.format(
                label.description, label.score)

        html_result += "</table>\n"

    return html_result

def get_text_detected(annotations):
    html_result = ""
    foundText = annotations.text.encode('utf-8').strip()
    if len(foundText) > 0:
        readLines = foundText.split('\n')
        res = ' | '.join(readLines)
        html_result += """
        <table>
        <tr><th style="text-align: left;">Detected items:</th></tr>
        """
        html_result += '<tr><td style="text-align: left;">{}</td></tr>\n'.format(res)
        html_result += '</table><br/>\n'
    return html_result

def render_document_text_annotation(bucket_name, blob_name, annotations,  out_bucket_name):
    bucket = storage_client.get_bucket(bucket_name)
    blob = bucket.blob(blob_name)
    byte_stream = BytesIO()
    blob.download_to_file(byte_stream)
    byte_stream.seek(0)
    image = Image.open(byte_stream)

    hasBounds = False
    bounds = get_text_annotations_bounds(annotations, FeatureType.PAGE)
    if len(bounds) > 0:
        draw_boxes(image, bounds, 'blue')
        hasBounds = True
    bounds = get_text_annotations_bounds(annotations, FeatureType.PARA)
    if len(bounds) > 0:
        draw_boxes(image, bounds, 'red')
        hasBounds = True
    bounds = get_text_annotations_bounds(annotations, FeatureType.WORD)
    if len(bounds) > 0:
        draw_boxes(image, bounds, 'green')
        hasBounds = True

    html_result = "<p>No text detected.</p>"
    if hasBounds:
        out_bucket = storage_client.get_bucket(out_bucket_name)
        file_name = "document_text.jpg"
        out_blob_name = '{}/{}'.format(os.path.splitext(blob_name)[0], file_name)
        tmp_fd, tmp_file_name = tempfile.mkstemp('.jpg')
        os.close(tmp_fd)
        image.save(tmp_file_name)
        out_blob = out_bucket.blob(out_blob_name)
        out_blob.upload_from_filename(tmp_file_name)
        out_blob.make_public()
        os.remove(tmp_file_name)
        html_result = '<img src={} alt="Text detection">'.format(file_name)

    return html_result


def annotate(bucket_name, blob_name, out_bucket_name):
    imageURI = 'gs://{}/{}'.format(bucket_name, blob_name)
    print('Annotating {}'.format(imageURI))
    response = vision_client.annotate_image({
        'image': {'source': {'image_uri': imageURI}},
        'features': [
            {'type': vision.enums.Feature.Type.FACE_DETECTION},
            {'type': vision.enums.Feature.Type.DOCUMENT_TEXT_DETECTION},
            {'type': vision.enums.Feature.Type.LABEL_DETECTION},
            {'type': vision.enums.Feature.Type.WEB_DETECTION}
        ]})

    html_result = """
    <html>
    <head>
    <style>
    table {
        font-family: arial, sans-serif;
        border-collapse: collapse;
    }

    td, th {
        border: 0px;
        text-align: center;
        padding: 8px;
    }

    </style>
    <title>Vision results</title>
    </head>
    <body>
    <h1>Faces detection</h1>
    """
    html_result += get_faces_likelihoods(response.face_annotations)
    html_result += render_face_annotations(bucket_name, blob_name, response.face_annotations, out_bucket_name)
    html_result += "\n<br/><h1>Document text detection</h1>\n"
    html_result += get_text_detected(response.full_text_annotation)
    html_result += render_document_text_annotation(bucket_name, blob_name, response.full_text_annotation, out_bucket_name)
    html_result += "\n<br/><h1>Web pages matching</h1>\n"
    html_result += get_web_annotations(bucket_name, blob_name, response.web_detection)
    html_result += "\n<h1>Labels detection</h1>\n"
    html_result += get_label_annotations(bucket_name, blob_name, response.label_annotations)
    html_result += """
    </body>
    </html>
    """

    out_bucket = storage_client.get_bucket(out_bucket_name)
    file_name = "detection_results.html"
    out_blob_name = '{}/{}'.format(os.path.splitext(blob_name)[0], file_name)
    tmp_fd, tmp_file_name = tempfile.mkstemp('.html')
    os.close(tmp_fd)
    with open(tmp_file_name, 'w') as text_file:
        text_file.write("{}".format(html_result))
    out_blob = out_bucket.blob(out_blob_name)
    out_blob.upload_from_filename(tmp_file_name)
    out_blob.make_public()
    os.remove(tmp_file_name)

def summarize(message):
    data = message.data.decode('utf-8')
    attributes = message.attributes

    event_type = attributes['eventType']
    bucket_id = attributes['bucketId']
    object_id = attributes['objectId']
    generation = attributes['objectGeneration']
    description = (
        '\tEvent type: {event_type}\n'
        '\tBucket ID: {bucket_id}\n'
        '\tObject ID: {object_id}\n'
        '\tGeneration: {generation}\n').format(
            event_type=event_type,
            bucket_id=bucket_id,
            object_id=object_id,
            generation=generation)

    if 'overwroteGeneration' in attributes:
        description += '\tOverwrote generation: %s\n' % (
            attributes['overwroteGeneration'])
    if 'overwrittenByGeneration' in attributes:
        description += '\tOverwritten by generation: %s\n' % (
            attributes['overwrittenByGeneration'])

    payload_format = attributes['payloadFormat']
    if payload_format == 'JSON_API_V1':
        object_metadata = json.loads(data)
        size = object_metadata['size']
        content_type = object_metadata['contentType']
        metageneration = object_metadata['metageneration']
        description += (
            '\tContent type: {content_type}\n'
            '\tSize: {object_size}\n'
            '\tMetageneration: {metageneration}\n').format(
                content_type=content_type,
                object_size=size,
                metageneration=metageneration)
    return description

def poll_notifications(project, subscription_name, out_bucket_name):

    subscriber = pubsub_v1.SubscriberClient()
    subscription_path = subscriber.subscription_path(
        project, subscription_name)

    def callback(message):
        print('Received message:\n{}'.format(summarize(message)))
        message.ack()
        if 'OBJECT_FINALIZE' ==  message.attributes['eventType']:
            annotate(message.attributes['bucketId'], message.attributes['objectId'],
                out_bucket_name)

    subscriber.subscribe(subscription_path, callback=callback)

    # The subscriber is non-blocking, so we must keep the main thread from
    # exiting to allow it to process messages in the background.
    print('Listening for messages on {}'.format(subscription_path))
    while True:
        time.sleep(60)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        'project',
        help='The ID of the project that owns the subscription')
    parser.add_argument('subscription',
                        help='The ID of the Pub/Sub subscription')
    parser.add_argument('outbucket',
                        help='The name of the Cloud Storage bucket where to store results')
    args = parser.parse_args()
poll_notifications(args.project, args.subscription, args.outbucket)

